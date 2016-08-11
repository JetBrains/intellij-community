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
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class FileHolderComposite implements FileHolder {
  private final Map<HolderType, FileHolder> myHolders;

  public FileHolderComposite(final Project project) {
    myHolders = new HashMap<>();
    myHolders.put(FileHolder.HolderType.UNVERSIONED, new VirtualFileHolder(project, FileHolder.HolderType.UNVERSIONED));
    myHolders.put(FileHolder.HolderType.ROOT_SWITCH, new SwitchedFileHolder(project, HolderType.ROOT_SWITCH));
    myHolders.put(FileHolder.HolderType.MODIFIED_WITHOUT_EDITING, new VirtualFileHolder(project, FileHolder.HolderType.MODIFIED_WITHOUT_EDITING));
    myHolders.put(FileHolder.HolderType.IGNORED, new IgnoredFilesCompositeHolder(project));
    myHolders.put(FileHolder.HolderType.LOCKED, new VirtualFileHolder(project, FileHolder.HolderType.LOCKED));
    myHolders.put(FileHolder.HolderType.LOGICALLY_LOCKED, new LogicallyLockedHolder(project));
  }

  public FileHolderComposite(final FileHolderComposite holder) {
    myHolders = new HashMap<>();
    for (FileHolder fileHolder : holder.myHolders.values()) {
      myHolders.put(fileHolder.getType(), fileHolder.copy());
    }
  }

  public FileHolder add(@NotNull final FileHolder fileHolder, final boolean copy) {
    final FileHolder added = copy ? fileHolder.copy() : fileHolder;
    myHolders.put(fileHolder.getType(), added);
    return added;
  }

  public void cleanAll() {
    for (FileHolder holder : myHolders.values()) {
      holder.cleanAll();
    }
  }

  public void cleanAndAdjustScope(final VcsModifiableDirtyScope scope) {
    for (FileHolder holder : myHolders.values()) {
      holder.cleanAndAdjustScope(scope);
    }
  }

  public FileHolder copy() {
    return new FileHolderComposite(this);
  }

  public FileHolder get(final HolderType type) {
    return myHolders.get(type);
  }

  public VirtualFileHolder getVFHolder(final HolderType type) {
    return (VirtualFileHolder) myHolders.get(type);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FileHolderComposite another = (FileHolderComposite) o;
    if (another.myHolders.size() != myHolders.size()) {
      return false;
    }

    for (Map.Entry<HolderType, FileHolder> entry : myHolders.entrySet()) {
      if (! entry.getValue().equals(another.myHolders.get(entry.getKey()))) {
        return false;
      }
    }

    return true;
  }

  @Override
  public int hashCode() {
    return myHolders != null ? myHolders.hashCode() : 0;
  }

  public HolderType getType() {
    throw new UnsupportedOperationException();
  }

  public IgnoredFilesHolder getIgnoredFileHolder() {
    return (IgnoredFilesHolder) myHolders.get(HolderType.IGNORED);
  }

  public void notifyVcsStarted(AbstractVcs vcs) {
    for (FileHolder fileHolder : myHolders.values()) {
      fileHolder.notifyVcsStarted(vcs);
    }
  }
}
