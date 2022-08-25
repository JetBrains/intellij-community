// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.usageAdapter;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.ReadWriteAccessUsageInfo2UsageAdapter;
import com.intellij.usages.UsageGroup;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import com.intellij.usages.similarity.clustering.UsageCluster;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class SimilarityReadWriteUsageInfo2UsageAdapter extends ReadWriteAccessUsageInfo2UsageAdapter implements SimilarityUsage {
  private final @NotNull Bag myFeatures;
  private final @NotNull ClusteringSearchSession mySession;

  private final @Nullable UsageCluster myCluster;
  private final @NotNull List<UsageGroup> myGroups;

  public SimilarityReadWriteUsageInfo2UsageAdapter(@NotNull UsageInfo usageInfo,
                                                   @NotNull ReadWriteAccessDetector.Access rwAccess,
                                                   @NotNull Bag features,
                                                   @NotNull ClusteringSearchSession session, @Nullable UsageCluster cluster) {
    super(usageInfo, rwAccess);
    myFeatures = features;
    mySession = session;
    myGroups = new ArrayList<>();
    myCluster = cluster;
  }

  @Override
  public @NotNull Bag getFeatures() {
    return myFeatures;
  }

  @Override
  public @NotNull ClusteringSearchSession getClusteringSession() {
    return mySession;
  }

  @Override
  public @Nullable UsageCluster getCluster() {
    return myCluster;
  }

  @Override
  public @NotNull List<UsageGroup> getUsageGroupData() {
    return myGroups;
  }

  @Override
  public void addUsageGroupData(@NotNull List<UsageGroup> groups) {
    myGroups.addAll(groups);
  }
}
