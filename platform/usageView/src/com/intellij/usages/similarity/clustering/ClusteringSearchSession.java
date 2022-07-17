// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.clustering;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
import com.intellij.util.MathUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Does usage clustering during the find usage process. Clusters are used on find usages results presentation.
 */
public class ClusteringSearchSession {
  public static final double MAXIMUM_SIMILARITY = 1.0;
  public static final double PRECISION = 1e-4;
  private final @NotNull List<UsageCluster> myClusters;
  private final double mySimilarityThreshold;

  public ClusteringSearchSession() {
    myClusters = Collections.synchronizedList(new ArrayList<>());
    mySimilarityThreshold = Registry.doubleValue("similarity.find.usages.groups.threshold");
  }

  public @NotNull List<UsageCluster> getClusters() {
    return myClusters;
  }

  @RequiresBackgroundThread
  public synchronized Usage clusterUsage(@NotNull Bag usageFeatures, @NotNull Usage similarUsageAdapter) {
    UsageCluster cluster = getTheMostSimilarCluster(usageFeatures);
    if (cluster == null) {
      cluster = createNewCluster();
    }
    cluster.addUsage((SimilarUsage)similarUsageAdapter);
    return similarUsageAdapter;
  }

  /**
   * This method is designed to use from {@link com.intellij.usages.UsageContextPanel#updateLayout(List)}
   */
  public @Nullable UsageCluster findCluster(@Nullable UsageInfo usageInfo) {
    synchronized (myClusters) {
      for (UsageCluster cluster : myClusters) {
        if (cluster.contains(usageInfo)) {
          return cluster;
        }
      }
    }
    return null;
  }

  @RequiresBackgroundThread
  @RequiresReadLock
  public @NotNull List<UsageCluster> getClustersForSelectedUsages(@NotNull ProgressIndicator indicator, @NotNull Set<Usage> selectedUsages) {
    //create new ArrayList from clusters to avoid concurrent modification and do all the needed sorting and filtering in non-blocking way
    return new ArrayList<>(getClusters()).stream()
      .map(cluster -> new UsageCluster(cluster.getOnlySelectedUsages(selectedUsages)))
      .sorted((o1, o2) -> {
        indicator.checkCanceled();
        return Integer.compare(o2.getUsages().size(), o1.getUsages().size());
      }).collect(Collectors.toList());
  }

  private @NotNull UsageCluster createNewCluster() {
    final UsageCluster newCluster = new UsageCluster();
    myClusters.add(newCluster);
    return newCluster;
  }

  private @Nullable UsageCluster getTheMostSimilarCluster(@NotNull Bag features) {
    UsageCluster mostUsageCluster = null;
    double maxSimilarity = 0;
    synchronized (myClusters) {
      for (UsageCluster cluster : myClusters) {
        double similarity = findMinimalSimilarity(cluster, features, mySimilarityThreshold);
        if (isCompleteMatch(similarity)) {
          return cluster;
        }
        if (lessThen(maxSimilarity, similarity)) {
          mostUsageCluster = cluster;
          maxSimilarity = similarity;
        }
      }
    }
    return mostUsageCluster;
  }

  private static boolean isCompleteMatch(double similarity) {
    return MathUtil.equals(similarity, MAXIMUM_SIMILARITY, PRECISION);
  }

  private static boolean lessThen(double similarity1, double similarity2) {
    return similarity1 < similarity2 && !MathUtil.equals(similarity1, similarity2, PRECISION);
  }

  private static double findMinimalSimilarity(@NotNull UsageCluster usageCluster, @NotNull Bag newUsageFeatures, double threshold) {
    double min = MAXIMUM_SIMILARITY;
    for (SimilarUsage usage : usageCluster.getUsages()) {
      final double similarity = jaccardSimilarity(usage.getFeatures(), newUsageFeatures);
      if (lessThen(similarity, min)) {
        min = similarity;
      }
      if (lessThen(min, threshold)) {
        return 0;
      }
    }
    return min;
  }

  public static @Nullable ClusteringSearchSession createClusteringSessionIfEnabled() {
    return isSimilarUsagesClusteringEnabled() ? new ClusteringSearchSession() : null;
  }

  public static boolean isSimilarUsagesClusteringEnabled() {
    return Registry.is("similarity.find.usages.enable") && ApplicationManager.getApplication().isInternal();
  }

  public static double jaccardSimilarity(@NotNull Bag bag1, @NotNull Bag bag2) {
    final int cardinality1 = bag1.getCardinality();
    final int cardinality2 = bag2.getCardinality();
    int intersectionSize = Bag.intersectionSize(bag1, bag2);
    return intersectionSize / (double)(cardinality1 + cardinality2 - intersectionSize);
  }
}
