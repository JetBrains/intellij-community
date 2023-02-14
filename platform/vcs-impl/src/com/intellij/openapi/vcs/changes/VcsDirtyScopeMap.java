// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.vcs.changes.VcsDirtyScopeManagerImpl.getDirtyScopeHashingStrategy;

public final class VcsDirtyScopeMap {
  private final Map<AbstractVcs, Set<FilePath>> myMap = new HashMap<>();

  @NotNull
  public Map<AbstractVcs, Set<FilePath>> asMap() {
    return myMap;
  }

  public void add(@NotNull AbstractVcs vcs, @NotNull FilePath filePath) {
    Set<FilePath> set = getVcsPathsSet(vcs);
    set.add(filePath);
  }

  public void addAll(@NotNull VcsDirtyScopeMap map) {
    for (Map.Entry<AbstractVcs, Set<FilePath>> entry : map.myMap.entrySet()) {
      Set<FilePath> set = getVcsPathsSet(entry.getKey());
      set.addAll(entry.getValue());
    }
  }

  private @NotNull Set<FilePath> getVcsPathsSet(@NotNull AbstractVcs vcs) {
    return myMap.computeIfAbsent(vcs, key -> {
      HashingStrategy<FilePath> strategy = getDirtyScopeHashingStrategy(key);
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
