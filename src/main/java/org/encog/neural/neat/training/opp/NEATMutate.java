package org.encog.neural.neat.training.opp;

import java.util.Random;

import org.encog.EncogError;
import org.encog.engine.network.activation.ActivationFunction;
import org.encog.mathutil.randomize.RandomChoice;
import org.encog.mathutil.randomize.RangeRandomizer;
import org.encog.ml.ea.genome.Genome;
import org.encog.ml.ea.opp.EvolutionaryOperator;
import org.encog.ml.ea.train.EvolutionaryAlgorithm;
import org.encog.neural.neat.NEATNeuronType;
import org.encog.neural.neat.NEATPopulation;
import org.encog.neural.neat.training.NEATGenome;
import org.encog.neural.neat.training.NEATInnovation;
import org.encog.neural.neat.training.NEATLinkGene;
import org.encog.neural.neat.training.NEATNeuronGene;
import org.encog.neural.neat.training.NEATTraining;

public class NEATMutate implements EvolutionaryOperator {

	private NEATTraining owner;
	private RandomChoice mutateChoices;
	private double mutateRate = 0.2;
	private double probNewMutate = 0.1;
	private double maxPertubation = 0.5;
	private int maxTries = 5;

	public NEATMutate() {
		this.mutateChoices = new RandomChoice(new double[] { 0.988, 0.001, 0.01, 0.001 });
		//this.mutateChoices = new RandomChoice(new double[] { 0.75, 0.1, 0.1, 0.05 });
	}

	@Override
	public void performOperation(Random rnd, Genome[] parents, int parentIndex,
			Genome[] offspring, int offspringIndex) {

		if (parents[0] != offspring[0]) {
			throw new EncogError(
					"This mutation only works when the offspring and parents are the same.  That is, it only mutates itself.");
		}

		NEATGenome genome = (NEATGenome) parents[0];

		int option = this.mutateChoices.generate(rnd);

		switch (option) {
		case 0: // mutate weight
			mutateWeights(genome);
			break;
		case 1: // add node
			if (genome.getNeuronsChromosome().size() < this.owner
					.getMaxIndividualSize()) {
				addNeuron(genome);
			}
			break;
		case 2: // add connection
			// now there's the chance a link may be added
			addLink(genome);
			break;
		case 3: // remove connection
			removeLink(genome);
			break;
		}

	}

	/**
	 * Mutate the genome by adding a link to this genome.
	 * 
	 * @param mutationRate
	 *            The mutation rate.
	 * @param chanceOfLooped
	 *            The chance of a self-connected neuron.
	 * @param numTrysToFindLoop
	 *            The number of tries to find a loop.
	 * @param numTrysToAddLink
	 *            The number of tries to add a link.
	 */
	private void addLink(NEATGenome target) {
		int countTrysToAddLink = this.maxTries;

		// the link will be between these two neurons
		long neuron1ID = -1;
		long neuron2ID = -1;

		// try to add a link
		while ((countTrysToAddLink--) > 0) {
			final NEATNeuronGene neuron1 = chooseRandomNeuron(target, true);
			final NEATNeuronGene neuron2 = chooseRandomNeuron(target, false);
			
			if( neuron1==null || neuron2==null ) {
				return;
			}

			if (!isDuplicateLink(target, neuron1.getId(), neuron2.getId()) // no duplicates
					&& (neuron2.getNeuronType() != NEATNeuronType.Bias) // do not go to a bias neuron
					&& (neuron1.getNeuronType() != NEATNeuronType.Output) // do not go from an output neuron
					&& (neuron2.getNeuronType() != NEATNeuronType.Input)) { // do not go to an input neuron

				neuron1ID = neuron1.getId();
				neuron2ID = neuron2.getId();
				break;
			}
		}

		// did we fail to find a link
		if ((neuron1ID < 0) || (neuron2ID < 0)) {
			return;
		}

		double r = ((NEATPopulation)target.getPopulation()).getWeightRange();
		createLink(target, neuron1ID, neuron2ID, RangeRandomizer.randomize(-r,r));
	}

	private void createLink(NEATGenome target, long neuron1ID, long neuron2ID, double weight) {

		// check to see if this innovation has already been tried
		NEATInnovation innovation = owner.getInnovations().findInnovation(neuron1ID, neuron2ID);
		
		// now create this link
		final NEATLinkGene linkGene = new NEATLinkGene(neuron1ID,
				neuron2ID, true, innovation.getInnovationID(),
				weight);
		target.getLinksChromosome().add(linkGene);
	}

	/**
	 * Mutate the genome by adding a neuron.
	 * 
	 * @param mutationRate
	 *            The mutation rate.
	 * @param numTrysToFindOldLink
	 *            The number of tries to find a link to split.
	 */
	public void addNeuron(NEATGenome target) {

		int countTrysToFindOldLink = this.maxTries;
		NEATPopulation pop = ((NEATPopulation)target.getPopulation());

		// the link to split
		NEATLinkGene splitLink = null;

		final int sizeBias = owner.getInputCount() + owner.getOutputCount()
				+ 10;

		// if there are not at least
		int upperLimit;
		if (target.getLinksChromosome().size() < sizeBias) {
			upperLimit = target.getNumGenes() - 1
					- (int) Math.sqrt(target.getNumGenes());
		} else {
			upperLimit = target.getNumGenes() - 1;
		}

		while ((countTrysToFindOldLink--) > 0) {
			// choose a link, use the square root to prefer the older links
			final int i = RangeRandomizer.randomInt(0, upperLimit);
			final NEATLinkGene link = (NEATLinkGene) target
					.getLinksChromosome().get(i);

			// get the from neuron
			final long fromNeuron = link.getFromNeuronID();

			if ((link.isEnabled())
					&& (((NEATNeuronGene) target.getNeuronsChromosome().get(
							getElementPos(target, fromNeuron))).getNeuronType() != NEATNeuronType.Bias)) {
				splitLink = link;
				break;
			}
		}

		if (splitLink == null) {
			return;
		}

		splitLink.setEnabled(false);

		final long from = splitLink.getFromNeuronID();
		final long to = splitLink.getToNeuronID();

		NEATInnovation innovation = owner.getInnovations().findInnovationSplit(from, to);
		
		// add the splitting neuron
		ActivationFunction af = this.owner.getNEATPopulation().getActivationFunctions().pick(new Random());
		
		target.getNeuronsChromosome().add(
				new NEATNeuronGene(NEATNeuronType.Hidden, af, innovation
						.getNeuronID(), innovation.getInnovationID()));

		// add the other two sides of the link
		createLink(target, from, innovation.getNeuronID(), splitLink.getWeight());
		createLink(target, innovation.getNeuronID(), to, pop.getWeightRange());
	}

	/**
	 * Choose a random neuron.
	 * 
	 * @param includeInput
	 *            Should the input and bias neurons be included.
	 * @return The random neuron.
	 */
	private NEATNeuronGene chooseRandomNeuron(NEATGenome target,
			final boolean choosingFrom) {
		int start;

		if (choosingFrom) {
			start = 0;
		} else {
			start = owner.getInputCount() + 1;
		}

		// if this network will not "cycle" then output neurons cannot be source
		// neurons
		if (!choosingFrom) {
			int ac = ((NEATPopulation) target.getPopulation())
					.getActivationCycles();
			if (ac == 1) {
				start += target.getOutputCount();
			}
		}
		
		int end = target
				.getNeuronsChromosome().size() - 1;
		
		// no neurons to pick!
		if( start>end ) {
			return null;
		}

		final int neuronPos = RangeRandomizer.randomInt(start, end);
		final NEATNeuronGene neuronGene = (NEATNeuronGene) target
				.getNeuronsChromosome().get(neuronPos);
		return neuronGene;

	}

	/**
	 * Get the specified neuron's index.
	 * 
	 * @param neuronID
	 *            The neuron id to check for.
	 * @return The index.
	 */
	private int getElementPos(NEATGenome target, final long neuronID) {

		for (int i = 0; i < target.getNeuronsChromosome().size(); i++) {
			final NEATNeuronGene neuronGene = (NEATNeuronGene) target
					.getNeuronsChromosome().get(i);
			if (neuronGene.getId() == neuronID) {
				return i;
			}
		}

		return -1;
	}

	/**
	 * Determine if this is a duplicate link.
	 * 
	 * @param fromNeuronID
	 *            The from neuron id.
	 * @param toNeuronID
	 *            The to neuron id.
	 * @return True if this is a duplicate link.
	 */
	public boolean isDuplicateLink(NEATGenome target, final long fromNeuronID,
			final long toNeuronID) {
		for (final NEATLinkGene linkGene : target.getLinksChromosome()) {
			if ((linkGene.getFromNeuronID() == fromNeuronID)
					&& (linkGene.getToNeuronID() == toNeuronID)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Mutate the weights.
	 * 
	 * @param mutateRate
	 *            The mutation rate.
	 * @param probNewMutate
	 *            The probability of a whole new weight.
	 * @param maxPertubation
	 *            The max perturbation.
	 */
	public void mutateWeights(NEATGenome target) {
		double weightRange = ((NEATPopulation)target.getPopulation()).getWeightRange();
		
		for (final NEATLinkGene linkGene : target.getLinksChromosome()) {
			if (Math.random() < mutateRate) {
				if (Math.random() < probNewMutate) {
					linkGene.setWeight(RangeRandomizer.randomize(-weightRange, weightRange));
				} else {
					double w = linkGene.getWeight()
							+ RangeRandomizer.randomize(-1, 1) * maxPertubation;
					w = NEATPopulation.clampWeight(w,weightRange);
					linkGene.setWeight(w);
				}
			}
		}
	}

	private boolean isNeuronNeeded(NEATGenome target, long neuronID) {

		// do not remove bias or input neurons or output
		for (NEATNeuronGene gene : target.getNeuronsChromosome()) {
			if (gene.getId() == neuronID) {
				NEATNeuronGene neuron = (NEATNeuronGene) gene;
				if (neuron.getNeuronType() == NEATNeuronType.Input
						|| neuron.getNeuronType() == NEATNeuronType.Bias
						|| neuron.getNeuronType() == NEATNeuronType.Output) {
					return true;
				}
			}
		}

		for (NEATLinkGene gene : target.getLinksChromosome()) {
			NEATLinkGene linkGene = (NEATLinkGene) gene;
			if (linkGene.getFromNeuronID() == neuronID) {
				return true;
			}
			if (linkGene.getToNeuronID() == neuronID) {
				return true;
			}
		}

		return false;
	}

	private void removeNeuron(NEATGenome target, long neuronID) {
		for (NEATNeuronGene gene : target.getNeuronsChromosome()) {
			if (gene.getId() == neuronID) {
				target.getNeuronsChromosome().remove(gene);
				return;
			}
		}
	}

	public void removeLink(NEATGenome target) {
		if (target.getLinksChromosome().size() < 5) {
			// don't remove from small genomes
			return;
		}

		// determine the target and remove
		int index = RangeRandomizer.randomInt(0, target.getLinksChromosome()
				.size() - 1);
		NEATLinkGene targetGene = (NEATLinkGene) target.getLinksChromosome()
				.get(index);
		target.getLinksChromosome().remove(index);

		// if this orphaned any nodes, then kill them too!
		if (!isNeuronNeeded(target, targetGene.getFromNeuronID())) {
			removeNeuron(target, targetGene.getFromNeuronID());
		}

		if (!isNeuronNeeded(target, targetGene.getToNeuronID())) {
			removeNeuron(target, targetGene.getToNeuronID());
		}
	}
	
	

	/**
	 * @return the mutateChoices
	 */
	public RandomChoice getMutateChoices() {
		return mutateChoices;
	}

	/**
	 * @param mutateChoices the mutateChoices to set
	 */
	public void setMutateChoices(RandomChoice mutateChoices) {
		this.mutateChoices = mutateChoices;
	}

	/**
	 * @return the mutateRate
	 */
	public double getMutateRate() {
		return mutateRate;
	}

	/**
	 * @param mutateRate the mutateRate to set
	 */
	public void setMutateRate(double mutateRate) {
		this.mutateRate = mutateRate;
	}

	/**
	 * @return the probNewMutate
	 */
	public double getProbNewMutate() {
		return probNewMutate;
	}

	/**
	 * @param probNewMutate the probNewMutate to set
	 */
	public void setProbNewMutate(double probNewMutate) {
		this.probNewMutate = probNewMutate;
	}

	/**
	 * @return the maxPertubation
	 */
	public double getMaxPertubation() {
		return maxPertubation;
	}

	/**
	 * @param maxPertubation the maxPertubation to set
	 */
	public void setMaxPertubation(double maxPertubation) {
		this.maxPertubation = maxPertubation;
	}

	/**
	 * @return the maxTries
	 */
	public int getMaxTries() {
		return maxTries;
	}

	/**
	 * @param maxTries the maxTries to set
	 */
	public void setMaxTries(int maxTries) {
		this.maxTries = maxTries;
	}

	@Override
	public int offspringProduced() {
		return 1;
	}

	@Override
	public int parentsNeeded() {
		return 1;
	}

	@Override
	public void init(EvolutionaryAlgorithm theOwner) {
		if (!(theOwner instanceof NEATTraining)) {
			throw new EncogError(
					"This operator only works with a NEATTraining trainer.");
		}

		this.owner = (NEATTraining) theOwner;
	}

}