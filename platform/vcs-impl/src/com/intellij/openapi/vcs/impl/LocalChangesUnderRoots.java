/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.LocalChangeList;
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
public class LocalChangesUnderRoots {
  private final ChangeListManager myChangeManager;
  private final ProjectLevelVcsManager myVcsManager;

  public LocalChangesUnderRoots(@NotNull ChangeListManager changeListManager, @NotNull ProjectLevelVcsManager projectLevelVcsManager) {
    myChangeManager = changeListManager;
    myVcsManager = projectLevelVcsManager;
  }

  @NotNull
  public Map<String, Map<VirtualFile, Collection<Change>>> getChangesByLists(@NotNull Collection<? extends VirtualFile> rootsToSave) {
    Map<String, Map<VirtualFile, Collection<Change>>> result = new HashMap<>();
    for (LocalChangeList list : myChangeManager.getChangeListsCopy()) {
      result.put(list.getName(), groupChanges(rootsToSave, list.getChanges()));
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
  @NotNull
  public Map<VirtualFile, Collection<Change>> getChangesUnderRoots(@NotNull Collection<? extends VirtualFile> rootsToSave) {
    return groupChanges(rootsToSave, myChangeManager.getAllChanges());
  }

  @NotNull
  private Map<VirtualFile, Collection<Change>> groupChanges(@NotNull Collection<? extends VirtualFile> rootsToSave,
                                                            @NotNull Collection<? extends Change> allChanges) {
    Map<VirtualFile, Collection<Change>> result = new HashMap<>();
    for (Change change : allChanges) {
      VirtualFile root = getRootForChange(change);
      if (root != null && rootsToSave.contains(root)) {
        Collection<Change> changes = result.computeIfAbsent(root, key -> new HashSet<>());
        changes.add(change);
      }
    }
    return result;
  }

  @Nullable
  private VirtualFile getRootForChange(@NotNull Change change) {
    FilePath bPath = ChangesUtil.getBeforePath(change);
    FilePath aPath = ChangesUtil.getAfterPath(change);

    VirtualFile root = getRootForPath(aPath);
    if (root == null && !Comparing.equal(bPath, aPath)) {
      root = getRootForPath(bPath);
    }
    return root;
  }

  @Nullable
  private VirtualFile getRootForPath(@Nullable FilePath file) {
    if (file == null) return null;
    return myVcsManager.getVcsRootFor(file);
  }
}
