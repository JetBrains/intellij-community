// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.google.common.collect.Iterables;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@ApiStatus.Internal
public class SwitchedFileHolder implements FileHolder {
  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;
  private final TreeMap<VirtualFile, Pair<Boolean, String>> myMap; // true = recursively, branch name

  public SwitchedFileHolder(final Project project) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myMap = new TreeMap<>(FilePathComparator.getInstance());
  }

  @Override
  public void cleanAll() {
    myMap.clear();
  }

  @Override
  public synchronized SwitchedFileHolder copy() {
    final SwitchedFileHolder copyHolder = new SwitchedFileHolder(myProject);
    copyHolder.myMap.putAll(myMap);
    return copyHolder;
  }

  @Override
  public void cleanUnderScope(@NotNull VcsDirtyScope scope) {
    if (myProject.isDisposed()) return;
    final Iterator<VirtualFile> iterator = myMap.keySet().iterator();
    while (iterator.hasNext()) {
      final VirtualFile file = iterator.next();
      if (isFileDirty(scope, file)) {
        iterator.remove();
      }
    }
  }

  private boolean isFileDirty(final VcsDirtyScope scope, final VirtualFile file) {
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

  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  @NotNull
  public Collection<VirtualFile> values() {
    return myMap.keySet();
  }

  public void addFile(final VirtualFile file, final String branch, final boolean recursive) {
    // without optimization here
    myMap.put(file, new Pair<>(recursive, branch));
  }

  public void removeFile(@NotNull final VirtualFile file) {
    myMap.remove(file);
  }

  public synchronized MultiMap<String, VirtualFile> getBranchToFileMap() {
    final MultiMap<String, VirtualFile> result = new MultiMap<>();
    for (final VirtualFile vf : myMap.keySet()) {
      result.putValue(myMap.get(vf).getSecond(), vf);
    }
    return result;
  }

  public synchronized boolean containsFile(@NotNull final VirtualFile file) {
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

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final SwitchedFileHolder that = (SwitchedFileHolder)o;
    return Iterables.elementsEqual(myMap.entrySet(), that.myMap.entrySet());
  }

  public int hashCode() {
    return myMap.hashCode();
  }
}
