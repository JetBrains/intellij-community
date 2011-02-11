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
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class LogicallyLockedHolder implements FileHolder {
  private final Map<VirtualFile, LogicalLock> myMap;
  private final Project myProject;

  public LogicallyLockedHolder(final Project project) {
    myProject = project;
    myMap = new HashMap<VirtualFile, LogicalLock>();
  }

  public void cleanAll() {
    myMap.clear();
  }

  public void add(final VirtualFile file, final LogicalLock lock) {
    myMap.put(file, lock);
  }

  public void cleanAndAdjustScope(VcsModifiableDirtyScope scope) {
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
