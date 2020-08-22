// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Utility class to sort changes by roots.
 *
 * @author irengrig
 * @author Kirill Likhodedov
 */
public final class LocalChangesUnderRoots {
  public static @NotNull Map<String, Map<VirtualFile, Collection<Change>>> getChangesByLists(@NotNull Collection<? extends VirtualFile> rootsToSave, @NotNull Project project) {
    Map<String, Map<VirtualFile, Collection<Change>>> result = new HashMap<>();
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    for (LocalChangeList list : ChangeListManagerImpl.getInstanceImpl(project).getChangeListsCopy()) {
      result.put(list.getName(), groupChanges(rootsToSave, list.getChanges(), vcsManager));
    }
    return result;
  }

  /**
   * Sort all changes registered in the {@link ChangeListManager} by VCS roots,
   * filtering out any roots except the specified ones.
   *
   * @param rootsToSave roots to search for changes only in them.
   * @return a map, whose keys are VCS roots (from the specified list) and values are {@link Change changes} from these roots.
   */
  public static @NotNull Map<VirtualFile, Collection<Change>> getChangesUnderRoots(@NotNull Collection<? extends VirtualFile> rootsToSave, @NotNull Project project) {
    return getChangesUnderRoots(rootsToSave, ChangeListManager.getInstance(project), project);
  }

  public static @NotNull Map<VirtualFile, Collection<Change>> getChangesUnderRoots(@NotNull Collection<? extends VirtualFile> rootsToSave,
                                                                                   @NotNull ChangeListManager changeListManager,
                                                                                   @NotNull Project project) {
    return groupChanges(rootsToSave, changeListManager.getAllChanges(), ProjectLevelVcsManager.getInstance(project));
  }

  private @NotNull static Map<VirtualFile, Collection<Change>> groupChanges(@NotNull Collection<? extends VirtualFile> rootsToSave,
                                                                           @NotNull Collection<? extends Change> allChanges,
                                                                           @NotNull ProjectLevelVcsManager vcsManager) {
    Map<VirtualFile, Collection<Change>> result = new HashMap<>();
    for (Change change : allChanges) {
      VirtualFile root = getRootForChange(change, vcsManager);
      if (root != null && rootsToSave.contains(root)) {
        Collection<Change> changes = result.computeIfAbsent(root, key -> new HashSet<>());
        changes.add(change);
      }
    }
    return result;
  }

  private static @Nullable VirtualFile getRootForChange(@NotNull Change change, @NotNull ProjectLevelVcsManager vcsManager) {
    FilePath bPath = ChangesUtil.getBeforePath(change);
    FilePath aPath = ChangesUtil.getAfterPath(change);

    VirtualFile root = getRootForPath(aPath, vcsManager);
    if (root == null && !Comparing.equal(bPath, aPath)) {
      root = getRootForPath(bPath, vcsManager);
    }
    return root;
  }

  private static @Nullable VirtualFile getRootForPath(@Nullable FilePath file, @NotNull ProjectLevelVcsManager vcsManager) {
    return file == null ? null : vcsManager.getVcsRootFor(file);
  }
}
