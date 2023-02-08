// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.clustering;

import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.usages.similarity.clustering.Distance.*;


/**
 * Does usage clustering during the find usage process. Clusters are used on find usages results presentation.
 */
public class ClusteringSearchSession {
  private final @NotNull List<@NotNull UsageCluster> myClusters;
  private final @NotNull Distance myDistance;

  public ClusteringSearchSession() {
    myClusters = Collections.synchronizedList(new ArrayList<>());
    myDistance = new Distance(Registry.doubleValue("similarity.find.usages.groups.threshold"));
  }

  public @NotNull List<@NotNull UsageCluster> getClusters() {
    return new ArrayList<>(myClusters);
  }

  @RequiresBackgroundThread
  public synchronized @NotNull SimilarUsage clusterUsage(@NotNull SimilarUsage similarUsageAdapter) {
    UsageCluster cluster = getTheMostSimilarCluster(similarUsageAdapter.getFeatures());
    if (cluster == null) {
      cluster = createNewCluster();
    }
    cluster.addUsage(similarUsageAdapter);
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
  public @NotNull List<@NotNull UsageCluster> getClustersForSelectedUsages(@NotNull Set<Usage> selectedUsages) {
    return getClusters().stream()
      .map(cluster -> new UsageCluster(cluster.getOnlySelectedUsages(selectedUsages)))
      .filter(usageCluster -> !usageCluster.getUsages().isEmpty())
      .sorted((o1, o2) -> {
        return Integer.compare(o2.getUsages().size(), o1.getUsages().size());
      }).collect(Collectors.toList());
  }


  public void updateClusters(@NotNull Collection<@NotNull UsageCluster> clusters) {
    synchronized (myClusters) {
      myClusters.clear();
      myClusters.addAll(clusters);
    }
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
        double similarity = myDistance.findMinimalSimilarity(cluster, features);
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

  public static @Nullable ClusteringSearchSession createClusteringSessionIfEnabled() {
    return isSimilarUsagesClusteringEnabled() ? new ClusteringSearchSession() : null;
  }

  public static boolean isSimilarUsagesClusteringEnabled() {
    return AdvancedSettings.getBoolean("ide.similar.usages.clustering.enable");
  }
}
