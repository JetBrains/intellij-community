// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.clustering;

import com.intellij.usageView.UsageInfo;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.UsageView;
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class represents the set of similar usages found during "Find usages" run.
 * {@link ClusteringSearchSession} defines which usages are considered similar and puts it the same {@link UsageCluster}.
 */
public class UsageCluster {

  private final Set<SimilarUsage> myUsages;

  public UsageCluster() {
    this.myUsages = Collections.synchronizedSet(new LinkedHashSet<>());
  }

  public UsageCluster(Set<SimilarUsage> usages) {
    this.myUsages = usages;
  }

  public void addUsage(@NotNull SimilarUsage usage) {
    myUsages.add(usage);
  }

  public Set<SimilarUsage> getUsages() {
    return myUsages;
  }

  public boolean contains(@Nullable UsageInfo usageInfo) {
    synchronized (myUsages) {
      for (SimilarUsage usage : myUsages) {
        if (usage instanceof UsageInfo2UsageAdapter && ((UsageInfo2UsageAdapter)usage).getUsageInfo().equals(usageInfo)) {
          return true;
        }
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
  public @NotNull Set<SimilarUsage> getOnlySelectedUsages(@NotNull Set<Usage> selectedUsages) {
    synchronized (myUsages) {
      return myUsages.stream().filter(e -> e.isValid() && selectedUsages.contains(e)).collect(Collectors.toSet());
    }
  }

  @Override
  public String toString() {
    synchronized (myUsages) {
      return "{\n" +
             myUsages.stream().map(usage -> usage.toString()).collect(Collectors.joining(",\n")) +
             "}\n";
    }
  }
}
