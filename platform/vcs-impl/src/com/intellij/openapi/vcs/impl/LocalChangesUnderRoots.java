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

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Utility class to sort changes by roots.
 *
 * @author irengrig
 * @author Kirill Likhodedov
 */
public class LocalChangesUnderRoots {
  private final ChangeListManager myChangeManager;
  private final ProjectLevelVcsManager myVcsManager;
  private VcsRoot[] myRoots;

  public LocalChangesUnderRoots(@NotNull ChangeListManager changeListManager, @NotNull ProjectLevelVcsManager projectLevelVcsManager) {
    myChangeManager = changeListManager;
    myVcsManager = projectLevelVcsManager;
  }

  public Map<String, Map<VirtualFile, Collection<Change>>> getChangesByLists(@NotNull Collection<VirtualFile> rootsToSave) {
    final Map<String, Map<VirtualFile, Collection<Change>>> result = new HashMap<>();
    myRoots = myVcsManager.getAllVcsRoots();

    final List<LocalChangeList> changeLists = myChangeManager.getChangeListsCopy();
    for (LocalChangeList list : changeLists) {
      final HashMap<VirtualFile, Collection<Change>> subMap = new HashMap<>();
      addChangesToMap(rootsToSave, subMap, list.getChanges());
      result.put(list.getName(), subMap);
    }
    return result;
  }

  /**
   * Sort all changes registered in the {@link ChangeListManager} by VCS roots,
   * filtering out any roots except the specified ones.
   * @param rootsToSave roots to search for changes only in them.
   * @return a map, whose keys are VCS roots (from the specified list) and values are {@link Change changes} from these roots.
   */
  @NotNull
  public Map<VirtualFile, Collection<Change>> getChangesUnderRoots(@NotNull Collection<VirtualFile> rootsToSave) {
    Map<VirtualFile, Collection<Change>> result = new HashMap<>();
    final Collection<Change> allChanges = myChangeManager.getAllChanges();
    myRoots = myVcsManager.getAllVcsRoots();

    addChangesToMap(rootsToSave, result, allChanges);
    return result;
  }

  private void addChangesToMap(Collection<VirtualFile> rootsToSave,
                               Map<VirtualFile, Collection<Change>> result,
                               Collection<Change> allChanges) {
    for (Change change : allChanges) {
      if (change.getBeforeRevision() != null) {
        addChangeToMap(result, change, change.getBeforeRevision(), rootsToSave);
      }
      if (change.getAfterRevision() != null) {
        addChangeToMap(result, change, change.getAfterRevision(), rootsToSave);
      }
    }
  }

  private void addChangeToMap(@NotNull Map<VirtualFile, Collection<Change>> result, @NotNull Change change, @NotNull ContentRevision revision, @NotNull Collection<VirtualFile> rootsToSave) {
    VirtualFile root = getRootForPath(revision.getFile(), rootsToSave);
    addChangeToMap(result, root, change);
  }

  @Nullable
  private VirtualFile getRootForPath(@NotNull FilePath file, @NotNull Collection<VirtualFile> rootsToSave) {
    final VirtualFile vf = ChangesUtil.findValidParentUnderReadAction(file);
    if (vf == null) {
      return null;
    }
    VirtualFile rootCandidate = null;
    for (VcsRoot root : myRoots) {
      if (VfsUtilCore.isAncestor(root.getPath(), vf, false)) {
        if (rootCandidate == null || VfsUtil.isAncestor(rootCandidate, root.getPath(), true)) { // in the case of nested roots choose the closest root
          rootCandidate = root.getPath();
        }
      }
    }
    if (! rootsToSave.contains(rootCandidate)) return null;
    return rootCandidate;
  }

  private static void addChangeToMap(@NotNull Map<VirtualFile, Collection<Change>> result, @Nullable VirtualFile root, @NotNull Change change) {
    if (root == null) {
      return;
    }
    Collection<Change> changes = result.get(root);
    if (changes == null) {
      changes = new HashSet<>();
      result.put(root, changes);
    }
    changes.add(change);
  }

}
