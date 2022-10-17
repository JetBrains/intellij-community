// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.usageAdapter;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This interface marks {@link com.intellij.usages.UsageInfo2UsageAdapter} implementation as a subject of clustering
 */
@ApiStatus.Experimental
public interface SimilarUsage extends Usage {

  /**
   * Each usage provides a {@link Bag} of features (string tokens) collected by {@link com.intellij.usages.similarity.features.UsageSimilarityFeaturesProvider} implementation
   *
   * @return a Bag of words {@link Bag} with string features related to usage context usage
   */
  @NotNull Bag getFeatures();

  @NotNull ClusteringSearchSession getClusteringSession();

  @NotNull UsageInfo getUsageInfo();

  @Nullable PsiElement getElement();
}
