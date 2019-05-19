// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Alexander Koshevoy
 */
public abstract class AbstractPathMapper implements PathMapper {

  @Nullable
  public static String convertToLocal(@NotNull String remotePath, @NotNull Iterable<? extends PathMappingSettings.PathMapping> mappings) {
    PathMappingSettings.BestMappingSelector selector = new PathMappingSettings.BestMappingSelector();
    for (PathMappingSettings.PathMapping mapping : mappings) {
      if (mapping.canReplaceRemote(remotePath)) {
        selector.consider(mapping, mapping.getRemoteLen());
      }
    }
    if (selector.get() != null) {
      //noinspection ConstantConditions
      return selector.get().mapToLocal(remotePath);
    }
    return null;
  }

  @Nullable
  public static String convertToRemote(@NotNull String localPath, @NotNull Collection<? extends PathMappingSettings.PathMapping> pathMappings) {
    PathMappingSettings.BestMappingSelector selector = new PathMappingSettings.BestMappingSelector();
    for (PathMappingSettings.PathMapping mapping : pathMappings) {
      if (mapping != null && mapping.canReplaceLocal(localPath)) {
        selector.consider(mapping, mapping.getLocalLen());
      }
    }

    if (selector.get() != null) {
      //noinspection ConstantConditions
      return selector.get().mapToRemote(localPath);
    }
    return null;
  }

  @NotNull
  @Override
  public final List<String> convertToRemote(@NotNull Collection<String> paths) {
    List<String> result = new ArrayList<>();
    for (String p : paths) {
      result.add(convertToRemote(p));
    }
    return result;
  }

  @Override
  public final boolean canReplaceRemote(@NotNull String remotePath) {
    for (PathMappingSettings.PathMapping mapping : getAvailablePathMappings()) {
      if (mapping.canReplaceRemote(remotePath)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public final boolean canReplaceLocal(@NotNull String localPath) {
    for (PathMappingSettings.PathMapping mapping : getAvailablePathMappings()) {
      if (mapping.canReplaceLocal(localPath)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  protected abstract Collection<PathMappingSettings.PathMapping> getAvailablePathMappings();
}
