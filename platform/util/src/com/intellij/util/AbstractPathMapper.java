/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author Alexander Koshevoy
 */
public abstract class AbstractPathMapper implements PathMapper {

  @Nullable
  public static String convertToLocal(@NotNull String remotePath, @NotNull Iterable<PathMappingSettings.PathMapping> mappings) {
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
  public static String convertToRemote(@NotNull String localPath, @NotNull Collection<PathMappingSettings.PathMapping> pathMappings) {
    PathMappingSettings.BestMappingSelector selector = new PathMappingSettings.BestMappingSelector();
    for (PathMappingSettings.PathMapping mapping : pathMappings) {
      if (mapping.canReplaceLocal(localPath)) {
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
    List<String> result = ContainerUtil.newArrayList();
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
