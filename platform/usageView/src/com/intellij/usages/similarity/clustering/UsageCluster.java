// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.clustering;

import com.intellij.usages.UsageGroup;
import com.intellij.usages.similarity.usageAdapter.SimilarityUsage;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

public class UsageCluster {

  private final int myIndex;
  private final Set<SimilarityUsage> myUsages = new CopyOnWriteArraySet<>();

  public UsageCluster(int index) {
    this.myIndex = index;
  }

  public int getIndex() {
    return myIndex;
  }

  public synchronized void addUsage(@NotNull SimilarityUsage usage) {
    myUsages.add(usage);
  }

  public @NotNull Set<SimilarityUsage> getUsages() {
    return myUsages;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UsageCluster)) return false;

    return myIndex == ((UsageCluster)o).myIndex;
  }

  @NotNull
  public Set<SimilarityUsage> getUsageFilteredByGroup(@NotNull Collection<@NotNull Collection<? extends UsageGroup>> groups) {
    return getUsages().stream().filter(e -> belongsToGroup(e, groups)).collect(Collectors.toSet());
  }

  public static boolean belongsToGroup(@NotNull SimilarityUsage info,
                                       @NotNull Collection<@NotNull Collection<? extends UsageGroup>> selectedGroupPaths) {
    for (Collection<? extends UsageGroup> selectedGroupPath : selectedGroupPaths) {
      if (ContainerUtil.intersection(selectedGroupPath, info.getUsageGroupData()).size() == selectedGroupPath.size()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return "{" +
           "id=" + myIndex + "\n" +
           ", myUsages=" + myUsages.stream().map(usage -> usage.toString()).collect(Collectors.joining(",\n")) +
           "}\n";
  }
}
