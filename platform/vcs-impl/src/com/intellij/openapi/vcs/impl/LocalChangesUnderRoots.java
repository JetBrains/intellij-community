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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vfs.VfsUtil;
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
  private final Project myProject;
  private final ChangeListManager myChangeManager;
  private final ProjectLevelVcsManager myVcsManager;
  private VcsRoot[] myRoots;

  public LocalChangesUnderRoots(Project project) {
    myProject = project;
    myChangeManager = ChangeListManager.getInstance(myProject);
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
  }

  /**
   * Sort all changes registered in the {@link ChangeListManager} by VCS roots,
   * filtering out any roots except the specified ones.
   * @param rootsToSave roots to search for changes only in them.
   * @return a map, whose keys are VCS roots (from the specified list) and values are {@link Change changes} from these roots.
   */
  @NotNull
  public Map<VirtualFile, Collection<Change>> getChangesUnderRoots(@NotNull Collection<VirtualFile> rootsToSave) {
    Map<VirtualFile, Collection<Change>> result = new HashMap<VirtualFile, Collection<Change>>();
    final Collection<Change> allChanges = myChangeManager.getAllChanges();
    myRoots = myVcsManager.getAllVcsRoots();

    for (Change change : allChanges) {
      if (change.getBeforeRevision() != null) {
        addChangeToMap(result, change, change.getBeforeRevision(), rootsToSave);
      }
      if (change.getAfterRevision() != null) {
        addChangeToMap(result, change, change.getAfterRevision(), rootsToSave);
      }
    }
    return result;
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
      if (VfsUtil.isAncestor(root.path, vf, false)) {
        if (rootCandidate == null || VfsUtil.isAncestor(rootCandidate, root.path, true)) { // in the case of nested roots choose the closest root
          rootCandidate = root.path;
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
      changes = new HashSet<Change>();
      result.put(root, changes);
    }
    changes.add(change);
  }

}
