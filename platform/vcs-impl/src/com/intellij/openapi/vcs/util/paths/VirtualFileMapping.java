// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.util.paths;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public final class VirtualFileMapping<T> {
  private final Map<VirtualFile, T> myVFMap = new HashMap<>();

  public void add(@NotNull VirtualFile file, @NotNull T value) {
    myVFMap.put(file, value);
  }

  public void remove(@NotNull VirtualFile file) {
    myVFMap.remove(file);
  }

  public void clear() {
    myVFMap.clear();
  }

  public @NotNull Collection<T> values() {
    return myVFMap.values();
  }

  public @NotNull Collection<Pair<VirtualFile, T>> entries() {
    return ContainerUtil.map(myVFMap.entrySet(), entry -> Pair.create(entry.getKey(), entry.getValue()));
  }

  public boolean containsKey(@NotNull VirtualFile file) {
    return myVFMap.containsKey(file);
  }

  public @Nullable T getMappingFor(@NotNull VirtualFile file) {
    Pair<@NotNull VirtualFile, @NotNull T> pair = getMappingAndRootFor(file);
    return pair != null ? pair.getSecond() : null;
  }


  public @Nullable Pair<@NotNull VirtualFile, @NotNull T> getMappingAndRootFor(@NotNull VirtualFile file) {
    while (file != null) {
      T value = myVFMap.get(file);
      if (value != null) return Pair.create(file, value);
      file = file.getParent();
    }
    return null;
  }
}
