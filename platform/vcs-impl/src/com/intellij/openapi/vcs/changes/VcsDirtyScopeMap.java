// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl.getDirtyScopeHashingStrategy;

@ApiStatus.Internal
public final class VcsDirtyScopeMap {
  private final Map<VcsRoot, Set<FilePath>> myMap = new HashMap<>();

  @NotNull
  public Map<VcsRoot, Set<FilePath>> asMap() {
    return myMap;
  }

  public void add(@NotNull VcsRoot vcs, @NotNull FilePath filePath) {
    Set<FilePath> set = getVcsPathsSet(vcs);
    set.add(filePath);
  }

  public void addAll(@NotNull VcsDirtyScopeMap map) {
    for (Map.Entry<VcsRoot, Set<FilePath>> entry : map.myMap.entrySet()) {
      Set<FilePath> set = getVcsPathsSet(entry.getKey());
      set.addAll(entry.getValue());
    }
  }

  private @NotNull Set<FilePath> getVcsPathsSet(@NotNull VcsRoot vcsRoot) {
    return myMap.computeIfAbsent(vcsRoot, key -> {
      HashingStrategy<FilePath> strategy = getDirtyScopeHashingStrategy(Objects.requireNonNull(key.getVcs()));
      return strategy == null ? new HashSet<>() : CollectionFactory.createCustomHashingStrategySet(strategy);
    });
  }

  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  public void clear() {
    myMap.clear();
  }
}
