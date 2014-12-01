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
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.VcsLogFilter;
import com.intellij.vcs.log.VcsLogFilterCollection;
import com.intellij.vcs.log.VcsLogRootFilter;
import com.intellij.vcs.log.VcsLogStructureFilter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class VcsLogFileFilter implements VcsLogFilter {
  @Nullable private final VcsLogStructureFilter myStructureFilter;
  @Nullable private final VcsLogRootFilter myRootFilter;

  public VcsLogFileFilter(@Nullable VcsLogStructureFilter structureFilter, @Nullable VcsLogRootFilter rootFilter) {
    myStructureFilter = structureFilter;
    myRootFilter = rootFilter;
  }

  @Nullable
  public VcsLogStructureFilter getStructureFilter() {
    return myStructureFilter;
  }

  @Nullable
  public VcsLogRootFilter getRootFilter() {
    return myRootFilter;
  }

  @NotNull
  private static Pair<Set<VirtualFile>, MultiMap<VirtualFile, VirtualFile>> collectRoots(@NotNull Collection<VirtualFile> files,
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

  // collect "nu voobche vse" (there is no english translation for this expression =) ) roots that might be visible
  // if filters unset returns just all roots
  @NotNull
  public static Set<VirtualFile> getAllVisibleRoots(@NotNull Collection<VirtualFile> roots, @NotNull VcsLogFilterCollection filters) {
    return getAllVisibleRoots(roots, filters.getRootFilter(), filters.getStructureFilter());
  }

  // same as other getAllVisibleRoots
  @NotNull
  public static Set<VirtualFile> getAllVisibleRoots(@NotNull Collection<VirtualFile> roots, @NotNull VcsLogFileFilter filter) {
    return getAllVisibleRoots(roots, filter.getRootFilter(), filter.getStructureFilter());
  }

  private static Set<VirtualFile> getAllVisibleRoots(@NotNull Collection<VirtualFile> roots, @Nullable VcsLogRootFilter rootFilter, @Nullable VcsLogStructureFilter structureFilter) {
    if (rootFilter == null && structureFilter == null) return new HashSet<VirtualFile>(roots);

    Collection<VirtualFile> fromRootFilter;
    if (rootFilter != null) {
      fromRootFilter = rootFilter.getRoots();
    } else {
      fromRootFilter = roots;
    }

    Collection<VirtualFile> fromStructureFilter;
    if (structureFilter != null) {
      Pair<Set<VirtualFile>, MultiMap<VirtualFile, VirtualFile>> rootsAndFiles = collectRoots(structureFilter.getFiles(), new HashSet<VirtualFile>(roots));
      fromStructureFilter = ContainerUtil.union(rootsAndFiles.first, rootsAndFiles.second.keySet());
    } else {
      fromStructureFilter = roots;
    }

    return new HashSet<VirtualFile>(ContainerUtil.intersection(fromRootFilter, fromStructureFilter));
  }

  // for given root returns files that are selected in it
  // if a root is visible as a whole returns empty set
  // same if root is invisible as a whole
  // so check that before calling this method
  @NotNull
  public static Set<VirtualFile> getFilteredFilesForRoot(@NotNull VirtualFile root, VcsLogFilterCollection filterCollection) {
    if (filterCollection.getStructureFilter() == null) return Collections.emptySet();

    Pair<Set<VirtualFile>, MultiMap<VirtualFile, VirtualFile>> rootsAndFiles =
      collectRoots(filterCollection.getStructureFilter().getFiles(), Collections.singleton(root));

    return new HashSet<VirtualFile>(rootsAndFiles.second.get(root));
  }
}
