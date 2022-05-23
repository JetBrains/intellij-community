// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.bag;

import org.jetbrains.annotations.NotNull;

public class BagsDistanceCalculator {
  private final double mySimilarityThreshold;
  private final @NotNull Bag myExistingUsageFeatures;

  public BagsDistanceCalculator(@NotNull Bag existingUsageFeatures, double similarityThreshold) {
    this.myExistingUsageFeatures = existingUsageFeatures;
    this.mySimilarityThreshold = similarityThreshold;
  }

  public double similarity(@NotNull Bag usageFeaturesToCheck) {
    final double similarity = jaccardSimilarity(usageFeaturesToCheck, myExistingUsageFeatures);
    if (similarity >= mySimilarityThreshold) {
      return similarity;
    }
    return 0.0;
  }

  public static double jaccardSimilarity(@NotNull Bag bag1, @NotNull Bag bag2) {
    final int cardinality1 = bag1.getCardinality();
    final int cardinality2 = bag2.getCardinality();
    int intersectionSize = Bag.intersectionSize(bag1, bag2);
    return intersectionSize / (double)(cardinality1 + cardinality2 - intersectionSize);
  }
}
