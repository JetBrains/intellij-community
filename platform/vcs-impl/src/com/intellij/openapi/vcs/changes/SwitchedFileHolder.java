/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;

// true = recursively, branch name
public class SwitchedFileHolder extends RecursiveFileHolder<Pair<Boolean, String>> {
  public SwitchedFileHolder(final Project project, final HolderType holderType) {
    super(project, holderType);
  }

  public synchronized SwitchedFileHolder copy() {
    final SwitchedFileHolder copyHolder = new SwitchedFileHolder(myProject, myHolderType);
    copyHolder.myMap.putAll(myMap);
    return copyHolder;
  }

  @Override
  protected boolean isFileDirty(final VcsDirtyScope scope, final VirtualFile file) {
    if (scope == null) return true;
    if (fileDropped(file)) return true;
    return scope.belongsTo(VcsUtil.getFilePath(file));
  }

  private boolean fileDropped(final VirtualFile file) {
    return !file.isValid() || myVcsManager.getVcsFor(file) == null;
  }
  
  public Map<VirtualFile, String> getFilesMapCopy() {
    final HashMap<VirtualFile, String> result = new HashMap<>();
    for (final VirtualFile vf : myMap.keySet()) {
      result.put(vf, myMap.get(vf).getSecond());
    }
    return result;
  }

  public void addFile(final VirtualFile file, final String branch, final boolean recursive) {
    // without optimization here
    myMap.put(file, new Pair<>(recursive, branch));
  }

  public synchronized MultiMap<String, VirtualFile> getBranchToFileMap() {
    final MultiMap<String, VirtualFile> result = new MultiMap<>();
    for (final VirtualFile vf : myMap.keySet()) {
      result.putValue(myMap.get(vf).getSecond(), vf);
    }
    return result;
  }

  @Override
  public synchronized boolean containsFile(final VirtualFile file) {
    final VirtualFile floor = myMap.floorKey(file);
    if (floor == null) return false;
    final SortedMap<VirtualFile, Pair<Boolean, String>> floorMap = myMap.headMap(floor, true);
    for (VirtualFile parent : floorMap.keySet()) {
      if (VfsUtilCore.isAncestor(parent, file, false)) {
        final Pair<Boolean, String> value = floorMap.get(parent);
        return parent.equals(file) || value.getFirst();
      }
    }
    return false;
  }

  @Nullable
  public String getBranchForFile(final VirtualFile file) {
    final VirtualFile floor = myMap.floorKey(file);
    if (floor == null) return null;
    final SortedMap<VirtualFile, Pair<Boolean, String>> floorMap = myMap.headMap(floor);
    for (VirtualFile parent : floorMap.keySet()) {
      if (VfsUtilCore.isAncestor(parent, file, false)) {
        return floorMap.get(parent).getSecond();
      }
    }
    return null;
  }
}
