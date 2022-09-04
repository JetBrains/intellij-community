// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.usageAdapter;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.ReadWriteAccessUsageInfo2UsageAdapter;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import org.jetbrains.annotations.NotNull;

public class SimilarReadWriteUsageInfo2UsageAdapter extends ReadWriteAccessUsageInfo2UsageAdapter implements SimilarUsage {
  private final @NotNull Bag myFeatures;
  private final @NotNull ClusteringSearchSession mySession;

  public SimilarReadWriteUsageInfo2UsageAdapter(@NotNull UsageInfo usageInfo,
                                                @NotNull ReadWriteAccessDetector.Access rwAccess,
                                                @NotNull Bag features,
                                                @NotNull ClusteringSearchSession session) {
    super(usageInfo, rwAccess);
    myFeatures = features;
    mySession = session;
  }

  @Override
  public @NotNull Bag getFeatures() {
    return myFeatures;
  }

  @Override
  public @NotNull ClusteringSearchSession getClusteringSession() {
    return mySession;
  }
}
