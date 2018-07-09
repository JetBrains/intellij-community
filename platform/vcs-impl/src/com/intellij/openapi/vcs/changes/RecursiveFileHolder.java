// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.google.common.collect.Iterables;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class RecursiveFileHolder implements IgnoredFilesHolder {

  private final Project myProject;
  private final HolderType myHolderType;
  private final Set<VirtualFile> myMap;

  public RecursiveFileHolder(final Project project, final HolderType holderType) {
    myProject = project;
    myMap = new HashSet<>();
    myHolderType = holderType;
  }

  @Override
  public void cleanAll() {
    myMap.clear();
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
    if (!containsFile(file)) {
      myMap.add(file);
    }
  }

  @Override
  public RecursiveFileHolder copy() {
    final RecursiveFileHolder copyHolder = new RecursiveFileHolder(myProject, myHolderType);
    copyHolder.myMap.addAll(myMap);
    return copyHolder;
  }

  @Override
  public boolean containsFile(@NotNull final VirtualFile file) {
    if (myMap.isEmpty()) return false;
    VirtualFile parent = file;
    while (parent != null) {
      if (myMap.contains(parent)) return true;
      parent = parent.getParent();
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
