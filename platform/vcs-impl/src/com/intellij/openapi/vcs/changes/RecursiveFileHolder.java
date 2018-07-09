// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class RecursiveFileHolder<T> implements IgnoredFilesHolder {

  protected final Project myProject;
  protected final ProjectLevelVcsManager myVcsManager;
  protected final HolderType myHolderType;
  protected final TreeMap<VirtualFile, T> myMap;
  protected final TreeMap<VirtualFile, T> myDirMap;

  public RecursiveFileHolder(final Project project, final HolderType holderType) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
    myMap = new TreeMap<>(FilePathComparator.getInstance());
    myDirMap = new TreeMap<>(FilePathComparator.getInstance());
    myHolderType = holderType;
  }

  public void cleanAll() {
    myMap.clear();
    myDirMap.clear();
  }

  protected Collection<VirtualFile> keys() {
    return myMap.keySet();
  }

  @Override
  public void notifyVcsStarted(AbstractVcs scope) {
  }

  public HolderType getType() {
    return myHolderType;
  }

  public void addFile(@NotNull final VirtualFile file) {
    if (! containsFile(file)) {
      myMap.put(file, null);
      if (file.isDirectory()) {
        myDirMap.put(file, null);
      }
    }
  }

  public void removeFile(@NotNull final VirtualFile file) {
    myMap.remove(file);
    if (file.isDirectory()) {
      myDirMap.remove(file);
    }
  }

  public RecursiveFileHolder copy() {
    final RecursiveFileHolder<T> copyHolder = new RecursiveFileHolder<>(myProject, myHolderType);
    copyHolder.myMap.putAll(myMap);
    copyHolder.myDirMap.putAll(myDirMap);
    return copyHolder;
  }

  public boolean containsFile(@NotNull final VirtualFile file) {
    if (myMap.containsKey(file)) return true;
    final VirtualFile floor = myDirMap.floorKey(file);
    if (floor == null) return false;
    final SortedMap<VirtualFile, T> floorMap = myDirMap.headMap(floor, true);
    for (VirtualFile parent : floorMap.keySet()) {
      if (VfsUtilCore.isAncestor(parent, file, false)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  public Collection<VirtualFile> values() {
    return myMap.keySet();
  }

  @Override
  public void cleanAndAdjustScope(@NotNull final VcsModifiableDirtyScope scope) {
    if (myProject.isDisposed()) return;
    final Iterator<VirtualFile> iterator = keys().iterator();
    while (iterator.hasNext()) {
      final VirtualFile file = iterator.next();
      if (isFileDirty(scope, file)) {
        iterator.remove();
      }
    }
  }

  protected boolean isFileDirty(final VcsDirtyScope scope, final VirtualFile file) {
    if (!file.isValid()) return true;
    final AbstractVcs[] vcsArr = new AbstractVcs[1];
    if (scope.belongsTo(VcsUtil.getFilePath(file), vcs -> vcsArr[0] = vcs)) {
      return true;
    }
    return vcsArr[0] == null;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final RecursiveFileHolder that = (RecursiveFileHolder)o;
    if (myMap.size() != that.myMap.size()) return false;
    final Iterator<Map.Entry<VirtualFile, T>> it1 = myMap.entrySet().iterator();
    final Iterator<Map.Entry<VirtualFile, T>> it2 = that.myMap.entrySet().iterator();

    while (it1.hasNext()) {
      if (! it2.hasNext()) return false;
      Map.Entry<VirtualFile, T> next1 = it1.next();
      Map.Entry<VirtualFile, T> next2 = it2.next();
      if (! Comparing.equal(next1.getKey(), next2.getKey()) || ! Comparing.equal(next1.getValue(), next2.getValue())) return false;
    }

    return true;
  }

  public int hashCode() {
    return myMap.hashCode();
  }
}
