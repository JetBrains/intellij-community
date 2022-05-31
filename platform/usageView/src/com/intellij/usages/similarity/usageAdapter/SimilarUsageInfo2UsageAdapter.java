// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.usageAdapter;

import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import org.jetbrains.annotations.NotNull;

public class SimilarUsageInfo2UsageAdapter extends UsageInfo2UsageAdapter implements SimilarUsage {

  private final @NotNull Bag myFeatures;
  private final @NotNull ClusteringSearchSession mySession;

  public SimilarUsageInfo2UsageAdapter(@NotNull UsageInfo usageInfo, @NotNull Bag features, @NotNull ClusteringSearchSession session) {
    super(usageInfo);
    myFeatures = features;
    mySession = session;
  }

  @Override
  public @NotNull ClusteringSearchSession getClusteringSession() {
    return mySession;
  }

  @Override
  public @NotNull Bag getFeatures() {
    return myFeatures;
  }
}
