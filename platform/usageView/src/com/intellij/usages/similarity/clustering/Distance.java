// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.clustering;

import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
import com.intellij.util.MathUtil;
import org.jetbrains.annotations.NotNull;

public class Distance {
  public static final double MINIMUM_SIMILARITY = 0.0;
  public static final double MAXIMUM_SIMILARITY = 1.0;
  public static final double PRECISION = 1e-4;
  private final double myThreshold;

  public Distance(double threshold) {
    myThreshold = threshold;
  }

  double findMinimalSimilarity(@NotNull UsageCluster usageCluster, @NotNull Bag newUsageFeatures) {
    double min = MAXIMUM_SIMILARITY;
    double max = MINIMUM_SIMILARITY;
    for (SimilarUsage usage : usageCluster.getUsages()) {
      final double similarity = jaccardSimilarityWithThreshold(usage.getFeatures(), newUsageFeatures, myThreshold);
      if (lessThen(similarity, min)) {
        min = similarity;
      }
      if (lessThen(max, similarity)) {
        max = similarity;
      }
      if (isCompleteMatch(max)) {
        return MAXIMUM_SIMILARITY;
      }
      if (lessThen(min, myThreshold)) {
        return MINIMUM_SIMILARITY;
      }
    }
    return min;
  }
  static boolean isCompleteMatch(double similarity) {
    return MathUtil.equals(similarity, MAXIMUM_SIMILARITY, PRECISION);
  }

  static boolean lessThen(double similarity1, double similarity2) {
    return similarity1 < similarity2 && !MathUtil.equals(similarity1, similarity2, PRECISION);
  }

  public static double jaccardSimilarityWithThreshold(@NotNull Bag bag1, @NotNull Bag bag2, double similarityThreshold) {
    final int cardinality1 = bag1.getCardinality();
    final int cardinality2 = bag2.getCardinality();
    if (lessThen(Math.min(cardinality1, cardinality2), Math.max(cardinality1, cardinality2) * similarityThreshold)) {
      return 0;
    }
    int intersectionSize = Bag.intersectionSize(bag1, bag2);
    return intersectionSize / (double)(cardinality1 + cardinality2 - intersectionSize);
  }
}
