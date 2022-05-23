// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.usageAdapter;

import com.intellij.usages.UsageGroup;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import com.intellij.usages.similarity.clustering.UsageCluster;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * This interface marks UsageInfo2UsageAdapter implementation as a subject of clustering
 */
@ApiStatus.Experimental
public interface SimilarityUsage {

  /**
   * Each usage provides a {@link Bag} of features (string tokens) collected by {@link com.intellij.usages.similarity.features.UsageSimilarityFeaturesProvider} implementation
   *
   * @return a Bag of words {@link Bag} with string features related to usage context usage
   */
  @NotNull Bag getFeatures();

  @NotNull ClusteringSearchSession getClusteringSession();

  @Nullable UsageCluster getCluster();

  /**
   * Pre-cached {@link UsageGroup} data from {@link com.intellij.usages.rules.UsageGroupingRule} which is calculated on Usage tree building
   *
   * @return list of usage {@link UsageGroup} obtained from {@link com.intellij.usages.rules.UsageGroupingRule}
   */
  @NotNull List<UsageGroup> getUsageGroupData();

  /**
   * This method is use for adding group data
   *
   * @param groups
   */
  void addUsageGroupData(@NotNull List<UsageGroup> groups);
}
