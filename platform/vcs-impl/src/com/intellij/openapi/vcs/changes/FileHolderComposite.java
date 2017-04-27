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
    add(new VirtualFileHolder(project, HolderType.UNVERSIONED));
    add(new SwitchedFileHolder(project, HolderType.ROOT_SWITCH));
    add(new SwitchedFileHolder(project, HolderType.SWITCHED));
    add(new VirtualFileHolder(project, HolderType.MODIFIED_WITHOUT_EDITING));
    add(new IgnoredFilesCompositeHolder(project));
    add(new VirtualFileHolder(project, HolderType.LOCKED));
    add(new LogicallyLockedHolder(project));
    add(new DeletedFilesHolder());
  }

  public FileHolderComposite(final FileHolderComposite holder) {
    myHolders = new HashMap<>();
    for (FileHolder fileHolder : holder.myHolders.values()) {
      addCopy(fileHolder);
    }
  }

  private void add(@NotNull FileHolder fileHolder) {
    myHolders.put(fileHolder.getType(), fileHolder);
  }

  private void addCopy(@NotNull FileHolder fileHolder) {
    myHolders.put(fileHolder.getType(), fileHolder.copy());
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
    return (VirtualFileHolder)myHolders.get(type);
  }

  public IgnoredFilesCompositeHolder getIgnoredFileHolder() {
    return (IgnoredFilesCompositeHolder)myHolders.get(HolderType.IGNORED);
  }

  public LogicallyLockedHolder getLogicallyLockedFileHolder() {
    return (LogicallyLockedHolder)myHolders.get(HolderType.LOGICALLY_LOCKED);
  }

  public SwitchedFileHolder getRootSwitchFileHolder() {
    return (SwitchedFileHolder)myHolders.get(HolderType.ROOT_SWITCH);
  }

  public SwitchedFileHolder getSwitchedFileHolder() {
    return (SwitchedFileHolder)myHolders.get(HolderType.SWITCHED);
  }

  public DeletedFilesHolder getDeletedFileHolder() {
    return (DeletedFilesHolder)myHolders.get(HolderType.DELETED);
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

  public void notifyVcsStarted(AbstractVcs vcs) {
    for (FileHolder fileHolder : myHolders.values()) {
      fileHolder.notifyVcsStarted(vcs);
    }
  }
}
