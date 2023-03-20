// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.openapi.util.Ref;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import com.intellij.usages.similarity.clustering.UsageCluster;
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.Map.Entry;

import static com.intellij.usages.similarity.clustering.Distance.jaccardDistanceExact;

public class SilhouetteScore {
  private final @NotNull ClusteringSearchSession myClusteringSearchSession;

  public SilhouetteScore(@NotNull ClusteringSearchSession clusteringSearchSession) {
    this.myClusteringSearchSession = clusteringSearchSession;
  }

  public double getSilhouetteScoreResult(){
    if(myClusteringSearchSession.getClusters().size() == 1) return Double.NaN;

    Ref<Double> overallSilhouetteScore = new Ref<>(0.0);
    Ref<Integer> usageCount = new Ref<>(0);
    myClusteringSearchSession.getClusters()
      .forEach(cluster -> {
        cluster.getUsages()
          .forEach(usage -> {
            usageCount.set(usageCount.get() + 1);
            double cohesion = getCohesionIndex(cluster, usage);
            double separation = getSeparationIndex(cluster, usage);
            double silhouetteScore = (separation - cohesion) / Math.max(separation, cohesion);
            if (Double.isNaN(silhouetteScore) || Double.isInfinite(silhouetteScore)) {
              silhouetteScore = 0.0;
            }
            double finalSilhouetteScore = silhouetteScore;
            overallSilhouetteScore.set(overallSilhouetteScore.get() + finalSilhouetteScore);
          });
      });
    return overallSilhouetteScore.get() / usageCount.get();
  }

  private static double getCohesionIndex(@NotNull UsageCluster cluster, @NotNull SimilarUsage similarUsage) {
    Ref<Double> cohesionIndex = new Ref<>(0.0);
    cluster.getUsages()
      .forEach(usage -> {
        cohesionIndex.set(cohesionIndex.get() + jaccardDistanceExact(usage.getFeatures(), similarUsage.getFeatures()));
      });

    return cohesionIndex.get() / (cluster.getUsages().size() - 1);
  }

  private double getSeparationIndex(@NotNull UsageCluster currentCluster, @NotNull SimilarUsage similarUsage){
    Map<UsageCluster, Double> separationIndexPerCluster = new HashMap<>();
    myClusteringSearchSession.getClusters()
      .forEach(cluster -> {
        if (!cluster.getUsages().contains(similarUsage)) {
          cluster.getUsages()
            .forEach(usage -> {
              double similarity = jaccardDistanceExact(usage.getFeatures(), similarUsage.getFeatures());
              separationIndexPerCluster.merge(cluster, similarity, (oldValue, newValue) -> oldValue + similarity);
          });
          separationIndexPerCluster.put(cluster, separationIndexPerCluster.get(cluster) / cluster.getUsages().size());
        }
      });

    separationIndexPerCluster.put(currentCluster, 1.0);
    UsageCluster closestCluster = separationIndexPerCluster
      .entrySet()
      .stream()
      .min(Entry.comparingByValue())
      .orElseThrow().getKey();

    return separationIndexPerCluster.get(closestCluster);
  }
}
