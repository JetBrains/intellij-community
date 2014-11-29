/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.vcs.log.data;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.VcsLogRootFilter;
import com.intellij.vcs.log.VcsLogStructureFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public class VcsLogFileFilterUtil {
  @NotNull
  static Pair<Set<VirtualFile>, MultiMap<VirtualFile, VirtualFile>> collectRoots(@NotNull Collection<VirtualFile> files,
                                                                                 @NotNull Set<VirtualFile> roots) {
    Set<VirtualFile> selectedRoots = new HashSet<VirtualFile>();
    MultiMap<VirtualFile, VirtualFile> selectedFiles = new MultiMap<VirtualFile, VirtualFile>();

    for (VirtualFile file : files) {
      if (roots.contains(file)) {
        selectedRoots.add(file);
      }
      else {
        VirtualFile candidateAncestorRoot = null;
        for (VirtualFile root : roots) {
          if (VfsUtilCore.isAncestor(root, file, false)) {
            if (candidateAncestorRoot == null || VfsUtilCore.isAncestor(candidateAncestorRoot, root, false)) {
              candidateAncestorRoot = root;
            }
          }
          else if (VfsUtilCore.isAncestor(file, root, false)) {
            selectedRoots.add(root);
          }
        }

        if (candidateAncestorRoot != null) {
          selectedFiles.putValue(candidateAncestorRoot, file);
        }
      }
    }

    return Pair.create(selectedRoots, selectedFiles);
  }

  @NotNull
  public static Pair<Set<VirtualFile>, MultiMap<VirtualFile, VirtualFile>> collectRootsAndFiles(@NotNull Set<VirtualFile> roots,
                                                                                                @Nullable VcsLogRootFilter rootFilter,
                                                                                                @Nullable VcsLogStructureFilter structureFilter) {
    if (rootFilter == null && structureFilter == null) return Pair.create(roots, MultiMap.<VirtualFile, VirtualFile>create());

    if (structureFilter == null) {
      return Pair.create((Set<VirtualFile>)new HashSet<VirtualFile>(rootFilter.getRoots()), MultiMap.<VirtualFile, VirtualFile>create());
    }
    Pair<Set<VirtualFile>, MultiMap<VirtualFile, VirtualFile>> selectedRootsAndFiles = collectRoots(structureFilter.getFiles(), roots);
    if (rootFilter == null) {
      return selectedRootsAndFiles;
    }
    return Pair.create(ContainerUtil.union(new HashSet<VirtualFile>(rootFilter.getRoots()), selectedRootsAndFiles.first),
                       selectedRootsAndFiles.second);
  }

  @Nullable
  public static Set<VirtualFile> collectRoots(@NotNull Set<VirtualFile> roots,
                                              @Nullable VcsLogRootFilter rootFilter,
                                              @Nullable VcsLogStructureFilter structureFilter) {
    if (rootFilter == null && structureFilter == null) return null;

    Pair<Set<VirtualFile>, MultiMap<VirtualFile, VirtualFile>> rootsAndFiles = collectRootsAndFiles(roots, rootFilter, structureFilter);
    return ContainerUtil.union(rootsAndFiles.first, rootsAndFiles.second.keySet());
  }
}
