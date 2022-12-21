// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache;

import org.junit.Test;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

/**
 * Test is flaky: it is inherently probabilistic, so could fail sometimes just by chance.
 */
public class FrugalQuantileEstimatorTest {

  public static final int SAMPLES = 100_000;

  private final ThreadLocalRandom rnd = ThreadLocalRandom.current();

  @Test
  public void estimatorIsGoodForUniform_0_100() {
    final int percentileToEstimate = 10;
    final double step = 0.5;
    final FrugalQuantileEstimator estimator = new FrugalQuantileEstimator(percentileToEstimate, step);

    final int[] samples = rnd.ints(0, 100)
      .limit(SAMPLES)
      .toArray();

    checkEstimatorIsGoodOnSamples(estimator, samples, expectedTolerance(step, SAMPLES));
  }

  @Test
  public void estimatorIsGoodForUniform_400_1000() {
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
    //RC: This is just a random guess -- don't know how to calculate estimation error
    return 3 * step;
  }

  private static void checkEstimatorIsGoodOnSamples(final FrugalQuantileEstimator estimator,
                                                    final int[] samples,
                                                    final double estimationTolerance) {
    final int percentileToEstimate = estimator.percentileToEstimate();
    //By definition: Nth percentile is the value at index=[Nth % of SIZE] of sorted samples
    final int truePercentileValue =
      IntStream.of(samples).sorted().skip((long)(samples.length * percentileToEstimate / 100.0)).findFirst().getAsInt();

    final int[] diffs = IntStream.of(samples).map(sample -> (int)(estimator.updateEstimation(sample) - truePercentileValue)).toArray();

    final double averageDivergence = IntStream.of(diffs).average().getAsDouble();

    //Assume fluctuations are Gaussian -> avg(N samples) should have variance 1/sqrt(N) of
    // a single sample variance:
    //final double tolerance = estimationTolerance / Math.sqrt(samples.length);
    assertTrue("avg(estimatedPercentile-truePercentile)=" + averageDivergence + ", but should be less than " + estimationTolerance,
               averageDivergence <= estimationTolerance);
  }
}