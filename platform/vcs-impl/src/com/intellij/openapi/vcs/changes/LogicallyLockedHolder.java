// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LogicallyLockedHolder implements FileHolder {
  private final Map<VirtualFile, LogicalLock> myMap;
  private final Project myProject;

  public LogicallyLockedHolder(final Project project) {
    myProject = project;
    myMap = new HashMap<>();
  }

  public void cleanAll() {
    myMap.clear();
  }

  public void add(final VirtualFile file, final LogicalLock lock) {
    myMap.put(file, lock);
  }

  public void cleanAndAdjustScope(@NotNull VcsModifiableDirtyScope scope) {
    VirtualFileHolder.cleanScope(myProject, myMap.keySet(), scope);
  }

  public FileHolder copy() {
    final LogicallyLockedHolder result = new LogicallyLockedHolder(myProject);
    result.myMap.putAll(myMap);
    return result;
  }

  public HolderType getType() {
    return HolderType.LOGICALLY_LOCKED;
  }

  @Override
  public void notifyVcsStarted(AbstractVcs vcs) {
  }

  public boolean containsKey(final VirtualFile vf) {
    return myMap.containsKey(vf);
  }

  public Map<VirtualFile, LogicalLock> getMap() {
    return Collections.unmodifiableMap(myMap);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    LogicallyLockedHolder that = (LogicallyLockedHolder)o;

    if (!myMap.equals(that.myMap)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myMap.hashCode();
  }
}
