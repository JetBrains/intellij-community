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
package com.intellij.vcs.log.impl;

import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.*;
import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.graph.VisibleGraph;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VcsLogUtil {
  public static final int MAX_SELECTED_COMMITS = 1000;

  @NotNull
  public static Map<VirtualFile, Set<VcsRef>> groupRefsByRoot(@NotNull Collection<VcsRef> refs) {
    return groupByRoot(refs, ref -> ref.getRoot());
  }

  @NotNull
  public static <T extends VcsShortCommitDetails> Map<VirtualFile, Set<T>> groupByRoot(@NotNull Collection<T> commits) {
    return groupByRoot(commits, commit -> commit.getRoot());
  }

  @NotNull
  private static <T> Map<VirtualFile, Set<T>> groupByRoot(@NotNull Collection<T> items, @NotNull Function<T, VirtualFile> rootGetter) {
    Map<VirtualFile, Set<T>> map =
      new TreeMap<>((o1, o2) -> o1.getPresentableUrl().compareTo(o2.getPresentableUrl()));
    for (T item : items) {
      VirtualFile root = rootGetter.fun(item);
      Set<T> set = map.get(root);
      if (set == null) {
        set = ContainerUtil.newHashSet();
        map.put(root, set);
      }
      set.add(item);
    }
    return map;
  }

  @NotNull
  public static List<Integer> getVisibleCommits(@NotNull final VisibleGraph<Integer> visibleGraph) {
    return new AbstractList<Integer>() {
      @Override
      public Integer get(int index) {
        return visibleGraph.getRowInfo(index).getCommit();
      }

      @Override
      public int size() {
        return visibleGraph.getVisibleCommitCount();
      }
    };
  }

  public static int compareRoots(@NotNull VirtualFile root1, @NotNull VirtualFile root2) {
    return root1.getPresentableUrl().compareTo(root2.getPresentableUrl());
  }

  @NotNull
  private static Set<VirtualFile> collectRoots(@NotNull Collection<FilePath> files, @NotNull Set<VirtualFile> roots) {
    Set<VirtualFile> selectedRoots = new HashSet<>();

    List<VirtualFile> sortedRoots = ContainerUtil.sorted(roots, (root1, root2) -> root1.getPath().compareTo(root2.getPath()));

    for (FilePath filePath : files) {
      VirtualFile virtualFile = filePath.getVirtualFile();

      if (virtualFile != null && roots.contains(virtualFile)) {
        // if a root itself is selected, add this root
        selectedRoots.add(virtualFile);
      }
      else {
        VirtualFile candidateAncestorRoot = null;
        for (VirtualFile root : sortedRoots) {
          if (FileUtil.isAncestor(VfsUtilCore.virtualToIoFile(root), filePath.getIOFile(), false)) {
            candidateAncestorRoot = root;
          }
        }
        if (candidateAncestorRoot != null) {
          selectedRoots.add(candidateAncestorRoot);
        }
      }

      // add all roots under selected path
      if (virtualFile != null) {
        for (VirtualFile root : roots) {
          if (VfsUtilCore.isAncestor(virtualFile, root, false)) {
            selectedRoots.add(root);
          }
        }
      }
    }

    return selectedRoots;
  }


  // collect absolutely all roots that might be visible
  // if filters unset returns just all roots
  @NotNull
  public static Set<VirtualFile> getAllVisibleRoots(@NotNull Collection<VirtualFile> roots,
                                                    @Nullable VcsLogRootFilter rootFilter,
                                                    @Nullable VcsLogStructureFilter structureFilter) {
    if (rootFilter == null && structureFilter == null) return new HashSet<>(roots);

    Collection<VirtualFile> fromRootFilter;
    if (rootFilter != null) {
      fromRootFilter = rootFilter.getRoots();
    }
    else {
      fromRootFilter = roots;
    }

    Collection<VirtualFile> fromStructureFilter;
    if (structureFilter != null) {
      fromStructureFilter = collectRoots(structureFilter.getFiles(), new HashSet<>(roots));
    }
    else {
      fromStructureFilter = roots;
    }

    return new HashSet<>(ContainerUtil.intersection(fromRootFilter, fromStructureFilter));
  }

  // for given root returns files that are selected in it
  // if a root is visible as a whole returns empty set
  // same if root is invisible as a whole
  // so check that before calling this method
  @NotNull
  public static Set<FilePath> getFilteredFilesForRoot(@NotNull final VirtualFile root, @NotNull VcsLogFilterCollection filterCollection) {
    if (filterCollection.getStructureFilter() == null) return Collections.emptySet();
    Collection<FilePath> files = filterCollection.getStructureFilter().getFiles();

    return new HashSet<>(ContainerUtil.filter(files, filePath -> {
      VirtualFile virtualFile = filePath.getVirtualFile();
      return root.equals(virtualFile) || FileUtil.isAncestor(VfsUtilCore.virtualToIoFile(root), filePath.getIOFile(), false);
    }));
  }

  // If this method stumbles on LoadingDetails instance it returns empty list
  @NotNull
  public static List<VcsFullCommitDetails> collectFirstPackOfLoadedSelectedDetails(@NotNull VcsLog log) {
    List<VcsFullCommitDetails> result = ContainerUtil.newArrayList();

    for (VcsFullCommitDetails next : log.getSelectedDetails()) {
      if (next instanceof LoadingDetails) {
        return Collections.emptyList();
      }
      else {
        result.add(next);
        if (result.size() >= MAX_SELECTED_COMMITS) break;
      }
    }

    return result;
  }

  @NotNull
  public static <T> List<T> collectFirstPack(@NotNull List<T> list, int max) {
    return list.subList(0, Math.min(list.size(), max));
  }

  @NotNull
  public static Set<VirtualFile> getVisibleRoots(@NotNull VcsLogUiImpl logUi) {
    VcsLogFilterCollection filters = logUi.getFilterUi().getFilters();
    Set<VirtualFile> roots = logUi.getDataPack().getLogProviders().keySet();
    return getAllVisibleRoots(roots, filters.getRootFilter(), filters.getStructureFilter());
  }

  @Nullable
  public static String getSingleFilteredBranch(@NotNull VcsLogBranchFilter filter, @NotNull VcsLogRefs refs) {
    String branchName = null;
    Set<VirtualFile> checkedRoots = ContainerUtil.newHashSet();
    for (VcsRef branch : refs.getBranches()) {
      if (!filter.matches(branch.getName())) continue;

      if (branchName == null) {
        branchName = branch.getName();
      }
      else if (!branch.getName().equals(branchName)) {
        return null;
      }

      if (checkedRoots.contains(branch.getRoot())) return null;
      checkedRoots.add(branch.getRoot());
    }

    return branchName;
  }

  public static void triggerUsage(@NotNull AnActionEvent e) {
    String text = e.getPresentation().getText();
    if (text != null) {
      triggerUsage(text);
    }
  }

  public static void triggerUsage(@NotNull String text) {
    UsageTrigger.trigger("vcs.log." + ConvertUsagesUtil.ensureProperKey(text).replace(" ", ""));
  }
}
