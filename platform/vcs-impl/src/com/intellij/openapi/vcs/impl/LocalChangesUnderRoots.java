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

import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangesUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.*;

/**
 * @author irengrig
 *         Date: 6/28/11
 *         Time: 8:46 AM
 */
public class LocalChangesUnderRoots {
  private final Project myProject;
  private final ChangeListManager myChangeManager;

  public LocalChangesUnderRoots(Project project) {
    myProject = project;
    myChangeManager = ChangeListManager.getInstance(myProject);
  }

  public Map<VirtualFile, Collection<Change>> getChangesUnderRoots(Collection<VirtualFile> rootsToSave) {
    final VcsRoot[] allRoots = ProjectLevelVcsManager.getInstance(myProject).getAllVcsRoots();
    final TreeMap<VirtualFile, Boolean> rootsMap = new TreeMap<VirtualFile, Boolean>(FilePathComparator.getInstance());
    for (VirtualFile toSave : rootsToSave) {
      rootsMap.put(toSave, true);
    }
    for (VcsRoot root : allRoots) {
      final VirtualFile floor = rootsMap.floorKey(root.path);
      if (floor != null && ! floor.equals(root.path)) {
        rootsMap.put(root.path, false);
      }
    }
    final Map<VirtualFile, Collection<Change>> result = new HashMap<VirtualFile, Collection<Change>>();
    final Collection<Change> allChanges = myChangeManager.getAllChanges();
    for (Change change : allChanges) {
      if (change.getBeforeRevision() != null) {
        addChangeIfUnderRoot(rootsMap, result, change, change.getBeforeRevision().getFile());
      }
      if (change.getAfterRevision() != null) {
        addChangeIfUnderRoot(rootsMap, result, change, change.getAfterRevision().getFile());
      }
    }
    return result;
  }

  private void addChangeIfUnderRoot(TreeMap<VirtualFile, Boolean> rootsMap, Map<VirtualFile, Collection<Change>> result, Change change,
                                    final FilePath path) {
    final VirtualFile vf = ChangesUtil.findValidParentUnderReadAction(path);
    final Map.Entry<VirtualFile, Boolean> entry = rootsMap.floorEntry(vf);
    if (entry != null && entry.getValue()) {
      Collection<Change> collection = result.get(entry.getKey());
      if (collection == null) {
        collection = new HashSet<Change>();
        result.put(entry.getKey(), collection);
      }
      collection.add(change);
    }
  }
}
