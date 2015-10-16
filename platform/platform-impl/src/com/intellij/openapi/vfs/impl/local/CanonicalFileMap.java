/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vfs.impl.local;

import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.local.FileWatcherResponsePath;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;

public final class CanonicalFileMap extends CanonicalFileMapper {
  private static final Logger LOG = Logger.getInstance(CanonicalFileMap.class);

  private static final class OriginalWatchPath {
    @NotNull public final String path;
    @NotNull public final MappingType mappingType;

    public OriginalWatchPath(@NotNull String path, @NotNull MappingType mappingType) {
      this.path = path;
      this.mappingType = mappingType;
    }
  }

  private final MultiMap<String, OriginalWatchPath> myCanonicalPathToOriginalPath = MultiMap.createSet();

  // The native filewatcher may report the root's parent as dirty when the root itself is dirty. In that situation, we want to record the
  // root itself as dirty. So this is a map from root parent to root.
  private final MultiMap<String, OriginalWatchPath> myRecursiveRootParents = MultiMap.createSet();

  /**
   * @return the canonical path for {@param file}
   */
  @Override
  @Nullable
  public String addMapping(@NotNull File file, @NotNull MappingType mappingType) {
    String canonicalFilePath = symLinkToRealPath(file);
    if (canonicalFilePath == null) {
      LOG.warn("Could not find canonical path for " + file.getPath());
      return null;
    }
    Collection<OriginalWatchPath> possibleOriginalPaths = myCanonicalPathToOriginalPath.getModifiable(canonicalFilePath);
    possibleOriginalPaths.add(new OriginalWatchPath(file.getPath(), mappingType));

    // If we are returned a path that is the parent of the actual dirty path, we need a way to see if it might indicate that a recursive
    // root is dirty. This allows us a cheap map lookup instead of child traversal.
    if (mappingType == MappingType.RECURSIVE) {
      File parentFile = file.getParentFile();
      if (parentFile != null) {
        Collection<OriginalWatchPath> originalWatchPaths = myRecursiveRootParents.getModifiable(symLinkToRealPath(parentFile));
        originalWatchPaths.add(new OriginalWatchPath(file.getPath(), MappingType.RECURSIVE));
      }
    }
    return canonicalFilePath;
  }

  @Override
  @NotNull
  public List<String> getMapping(@NotNull FileWatcherResponsePath canonicalPath) {
    List<String> pathComponents = FileUtil.splitPath(canonicalPath.getPath());
    List<String> originalPathsToFile = Lists.newArrayList();

    File runningPath = null;
    for (int i = 0; i < pathComponents.size(); ++i) {
      if (runningPath == null) {
        runningPath = new File(pathComponents.get(i) + File.separator);
      }
      else {
        runningPath = new File(runningPath, pathComponents.get(i));
      }
      Collection<OriginalWatchPath> possibleOriginalPaths = myCanonicalPathToOriginalPath.get(runningPath.getPath());
      for (OriginalWatchPath possibleOriginalPath : possibleOriginalPaths) {
        switch (possibleOriginalPath.mappingType) {
          case FLAT:
            // If we were returned the parent of a dirty path, the last item in the path should be the flat root.
            // Else
            // If we were returned the actual dirty path, the second to last item in the path should be the flat root and the last item in
            // the path should be the dirty file name
            if (canonicalPath.isParentOfDirtyPath() && i == (pathComponents.size() - 1)) {
              originalPathsToFile.add(possibleOriginalPath.path);
            }
            else if (!canonicalPath.isParentOfDirtyPath() && (i + 1) == (pathComponents.size() - 1)) {
              originalPathsToFile.add(combine(possibleOriginalPath.path, pathComponents, i + 1));
            }
            break;
          case RECURSIVE:
            originalPathsToFile.add(combine(possibleOriginalPath.path, pathComponents, i + 1));
            break;
          default:
            LOG.error("Unhandled mapping type: " + possibleOriginalPath.mappingType);
        }
      }
    }

    // If we were returned a path that is the parent of the actual dirty path, we need to see if it might indicate that a recursive root is
    // dirty.
    if (runningPath != null && canonicalPath.isParentOfDirtyPath()) {
      Collection<OriginalWatchPath> originalWatchPaths = myRecursiveRootParents.get(runningPath.getPath());
      for (OriginalWatchPath originalWatchPath : originalWatchPaths) {
        originalPathsToFile.add(originalWatchPath.path);
      }
    }
    return originalPathsToFile;
  }
}
