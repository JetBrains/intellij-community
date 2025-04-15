// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.clustering;

import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageView;
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

/**
 * This class represents the set of similar usages found during "Find usages" run.
 * {@link ClusteringSearchSession} defines which usages are considered similar and puts it the same {@link UsageCluster}.
 */
public class UsageCluster {

  private final @NotNull Set<@NotNull SimilarUsage> myUsages;

  public UsageCluster() {
    this.myUsages = new CopyOnWriteArraySet<>();
  }

  public UsageCluster(@NotNull Set<@NotNull SimilarUsage> usages) {
    this.myUsages = usages;
  }

  public void addUsage(@NotNull SimilarUsage usage) {
    myUsages.add(usage);
  }

  public @NotNull @Unmodifiable Set<@NotNull SimilarUsage> getUsages() {
    return myUsages;
  }

  public boolean contains(@Nullable UsageInfo usageInfo) {
    for (SimilarUsage usage : myUsages) {
      if (usage.getUsageInfo().equals(usageInfo)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns the set of usages in this cluster which are selected in the tree.
   * {@link UsageView#getSelectedUsages()}
   * @param selectedUsages - a set of usages selected in tree (for selected group node in usage tree it returns all underlying usages)
   * @return filtered set of usages
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  public @NotNull @Unmodifiable Set<@NotNull SimilarUsage> getOnlySelectedUsages(@NotNull Set<@NotNull Usage> selectedUsages) {
    return myUsages.stream().filter(e -> selectedUsages.contains(e)).collect(Collectors.toSet());
  }

  @Override
  public String toString() {
    return "{\n" +
           myUsages.stream().map(usage -> usage.toString()).collect(Collectors.joining(",\n")) +
           "}\n";
  }
}
