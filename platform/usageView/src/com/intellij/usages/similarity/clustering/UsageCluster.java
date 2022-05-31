// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.clustering;

import com.intellij.usages.Usage;
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

public class UsageCluster {

  private final int myIndex;
  private final Set<SimilarUsage> myUsages = new CopyOnWriteArraySet<>();

  public UsageCluster(int index) {
    this.myIndex = index;
  }

  public int getIndex() {
    return myIndex;
  }

  public void addUsage(@NotNull SimilarUsage usage) {
    myUsages.add(usage);
  }

  public @NotNull Set<SimilarUsage> getUsages() {
    return myUsages;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof UsageCluster)) return false;

    return myIndex == ((UsageCluster)o).myIndex;
  }

  public @NotNull Set<SimilarUsage> getOnlySelectedUsages(Set<Usage> selectedUsage) {
    return getUsages().stream().filter(e -> selectedUsage.contains(e)).collect(Collectors.toSet());
  }

  @Override
  public String toString() {
    return "{" +
           "id=" + myIndex + "\n" +
           ", myUsages=" + myUsages.stream().map(usage -> usage.toString()).collect(Collectors.joining(",\n")) +
           "}\n";
  }
}
