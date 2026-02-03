// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import org.junit.Test;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

/**
 * Test is flaky: it is inherently probabilistic, so could fail sometimes just by
 * chance. But I run it ~10k times without failure, so, probably, this test is
 * slightly less flaky than the average test in our testsuite.
 */
public class FrugalQuantileEstimatorTest {

  public static final int SAMPLES = 100_000;

  private final ThreadLocalRandom rnd = ThreadLocalRandom.current();

  @Test
  public void estimatorIsGoodForUniformDistribution_0_100() {
    final int percentileToEstimate = 10;
    final double step = 0.5;
    final FrugalQuantileEstimator estimator = new FrugalQuantileEstimator(percentileToEstimate, step);

    final int[] samples = rnd.ints(0, 100)
      .limit(SAMPLES)
      .toArray();

    checkEstimatorIsGoodOnSamples(estimator, samples, expectedTolerance(step, SAMPLES));
  }

  @Test
  public void estimatorIsGoodForUniformDistribution_400_1000() {
    final int percentileToEstimate = 10;
    final double step = 0.5;
    final FrugalQuantileEstimator estimator = new FrugalQuantileEstimator(percentileToEstimate, step);
    final int[] samples = rnd.ints(400, 1000)
      .limit(SAMPLES)
      .toArray();

    checkEstimatorIsGoodOnSamples(estimator, samples, expectedTolerance(step, SAMPLES));
  }

  @Test
  public void estimatorIsGoodForTruncatedGaussian_0_100() {
    final int mean = 0;
    final int stddev = 100;

    final int percentileToEstimate = 10;
    final double step = 1;
    final int startWith = mean;

    final FrugalQuantileEstimator estimator = new FrugalQuantileEstimator(percentileToEstimate, step, startWith);
    final int[] samples = new int[SAMPLES];
    for (int i = 0; i < samples.length; i++) {
      final double gaussian = rnd.nextGaussian(mean, stddev);
      samples[i] = (int)Math.abs(gaussian > 2 * stddev ? 2 * stddev : gaussian);
    }

    checkEstimatorIsGoodOnSamples(estimator, samples, expectedTolerance(step, SAMPLES));
  }


  private static double expectedTolerance(final double step,
                                          final int sampleSize) {
    //RC: This is just a guess -- don't know how to calculate estimation error
    return 4 * step;
  }

  private static void checkEstimatorIsGoodOnSamples(final FrugalQuantileEstimator estimator,
                                                    final int[] samples,
                                                    final double estimationTolerance) {
    final int percentileToEstimate = estimator.percentileToEstimate();

    //By definition: Nth percentile is the value at index=[Nth % of SIZE] of sorted samples
    final int samplesBelowPercentile = (int)(samples.length * percentileToEstimate / 100.0);
    final int truePercentileValue = IntStream.of(samples)
      .sorted()
      .skip(samplesBelowPercentile)
      .findFirst().getAsInt();

    //apply estimator along with the samples, and diff its estimation against true percentile:
    final int[] diffs = IntStream.of(samples)
      .map(sample -> (int)(estimator.updateEstimation(sample) - truePercentileValue))
      .toArray();

    final double averageDivergence = IntStream.of(diffs).average().getAsDouble();

    assertTrue("avg(estimatedPercentile-truePercentile)=" + averageDivergence + ", but should be less than " + estimationTolerance,
               averageDivergence <= estimationTolerance);
  }
}