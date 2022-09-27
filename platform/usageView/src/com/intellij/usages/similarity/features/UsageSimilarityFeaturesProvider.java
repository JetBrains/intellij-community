// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.features;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This class provides features for clustering usages in Find Usages results.
 * {@link com.intellij.usages.similarity.usageAdapter.SimilarUsage} implementations store these features.
 * These features are used in {@link com.intellij.usages.similarity.clustering.ClusteringSearchSession} for calculating similarity between usages.
 */
@ApiStatus.Experimental
public interface UsageSimilarityFeaturesProvider {
  @ApiStatus.Experimental
  ExtensionPointName<UsageSimilarityFeaturesProvider> EP_NAME = ExtensionPointName.create("com.intellij.usageFeaturesProvider");

  /**
   * Calculates a Bag-of-words collection of string features for {@link UsageInfo#getElement()} in {@link com.intellij.usages.UsageInfoToUsageConverter}.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @NotNull Bag getFeatures(@NotNull PsiElement usage);
}
