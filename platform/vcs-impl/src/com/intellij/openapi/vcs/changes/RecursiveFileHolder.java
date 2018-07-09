// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.google.common.collect.Iterables;
import com.intellij.openapi.diff.impl.patch.formove.FilePathComparator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.TreeSet;

public class RecursiveFileHolder<T> implements IgnoredFilesHolder {

  private final Project myProject;
  private final HolderType myHolderType;
  private final TreeSet<VirtualFile> myMap;
  private final TreeSet<VirtualFile> myDirMap;

  public RecursiveFileHolder(final Project project, final HolderType holderType) {
    myProject = project;
    myMap = new TreeSet<>(FilePathComparator.getInstance());
    myDirMap = new TreeSet<>(FilePathComparator.getInstance());
    myHolderType = holderType;
  }

  @Override
  public void cleanAll() {
    myMap.clear();
    myDirMap.clear();
  }

  @Override
  public void notifyVcsStarted(AbstractVcs scope) {
  }

  @Override
  public HolderType getType() {
    return myHolderType;
  }

  @Override
  public void addFile(@NotNull final VirtualFile file) {
    if (! containsFile(file)) {
      myMap.add(file);
      if (file.isDirectory()) {
        myDirMap.add(file);
      }
    }
  }

  @Override
  public RecursiveFileHolder copy() {
    final RecursiveFileHolder<T> copyHolder = new RecursiveFileHolder<>(myProject, myHolderType);
    copyHolder.myMap.addAll(myMap);
    copyHolder.myDirMap.addAll(myDirMap);
    return copyHolder;
  }

  @Override
  public boolean containsFile(@NotNull final VirtualFile file) {
    if (myMap.contains(file)) return true;
    final VirtualFile floor = myDirMap.floor(file);
    if (floor == null) return false;
    final NavigableSet<VirtualFile> floorMap = myDirMap.headSet(floor, true);
    for (VirtualFile parent : floorMap) {
      if (VfsUtilCore.isAncestor(parent, file, false)) {
        return true;
      }
    }
    return false;
  }

  @Override
  @NotNull
  public Collection<VirtualFile> values() {
    return myMap;
  }

  @Override
  public void cleanAndAdjustScope(@NotNull final VcsModifiableDirtyScope scope) {
    if (myProject.isDisposed()) return;
    final Iterator<VirtualFile> iterator = myMap.iterator();
    while (iterator.hasNext()) {
      final VirtualFile file = iterator.next();
      if (isFileDirty(scope, file)) {
        iterator.remove();
      }
    }
  }

  private static boolean isFileDirty(final VcsDirtyScope scope, final VirtualFile file) {
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
    return Iterables.elementsEqual(myMap, that.myMap);
  }

  public int hashCode() {
    return myMap.hashCode();
  }
}
