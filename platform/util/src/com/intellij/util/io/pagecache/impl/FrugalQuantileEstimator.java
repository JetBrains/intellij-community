// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io.pagecache.impl;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Probabilistically estimates N-th quantile of running stream of integer values.
 * Estimation is not very precise (especially for too high/too low percentiles),
 * and not very fast converging, but it's extremely cheap and 'running', i.e. adjusts
 * as data stream underlying distribution changes:
 * <pre>
 *  var estimator = new FrugalQuantileEstimator(30, 0, 1);
 *  for(value : ...){
 *    int estimated30thPercentileOfValues = estimator.updateEstimation(value);
 *  }
 * </pre>
 *
 * <p>
 * Uses algorithm from:
 * "Frugal streaming for estimating quantiles" in
 * Space-Efficient Data Structures, Streams, and Algorithms.
 * Berlin, Germany: Springer, 2013, pp. 77–96
 */
public final class FrugalQuantileEstimator {

  /**
   * That percentile to estimate: value in [0..100).
   * Keep in mind: the farther from the median (50%) the harder it is for the algorithm
   * to converge.
   */
  private int targetPercentileToEstimate;
  private double currentEstimation;

  private final double step;

  public FrugalQuantileEstimator(final int percentileToEstimate,
                                 final double step) {
    this(percentileToEstimate, step, /*initialEstimation: */ 0);
  }

  public FrugalQuantileEstimator(final int percentileToEstimate,
                                 final double step,
                                 final double initialEstimation) {
    if (step <= 0) {
      throw new IllegalArgumentException("step(=" + step + ") must be >0");
    }
    updateTargetPercentile(percentileToEstimate);
    this.currentEstimation = initialEstimation;
    this.step = step;
  }

  /**
   * Consumes next value from the stream, and returns current estimation of
   * {@link #targetPercentileToEstimate}
   */
  public double updateEstimation(final int value) {
    final ThreadLocalRandom rnd = ThreadLocalRandom.current();
    final int randomValue100 = rnd.nextInt(100);

    if (value > currentEstimation && randomValue100 <= targetPercentileToEstimate) {
      currentEstimation += step;
    }
    else if (value < currentEstimation && randomValue100 > targetPercentileToEstimate) {
      currentEstimation -= step;
    }
    return currentEstimation();
  }

  public void updateTargetPercentile(final int newPercentileToEstimate) {
    if (newPercentileToEstimate <= 0 || newPercentileToEstimate >= 100) {
      throw new IllegalArgumentException("percentileToEstimate(=" + newPercentileToEstimate + ") must be in (0, 100)");
    }
    this.targetPercentileToEstimate = newPercentileToEstimate;
  }

  public int percentileToEstimate() {
    return targetPercentileToEstimate;
  }

  public double currentEstimation() {
    return currentEstimation;
  }

  @Override
  public String toString() {
    return "FrugalQuantileEstimator[" +
           "target: " + targetPercentileToEstimate + " %-ile" +
           ", current: " + currentEstimation +
           ", step: " + step +
           ']';
  }
}
