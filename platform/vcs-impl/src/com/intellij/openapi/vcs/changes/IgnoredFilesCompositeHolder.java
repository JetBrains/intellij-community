// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class IgnoredFilesCompositeHolder implements FileHolder {
  private final Map<AbstractVcs, IgnoredFilesHolder> myVcsIgnoredHolderMap;
  private IgnoredFilesHolder myIdeIgnoredFilesHolder;
  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;

  public IgnoredFilesCompositeHolder(final Project project) {
    super();
    myProject = project;
    myVcsIgnoredHolderMap = new HashMap<>();
    myIdeIgnoredFilesHolder = new RecursiveFileHolder<>(myProject, HolderType.IGNORED);
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
  }

  @Override
  public void cleanAll() {
    myVcsIgnoredHolderMap.values().forEach(IgnoredFilesHolder::cleanAll);
    myVcsIgnoredHolderMap.clear();
    myIdeIgnoredFilesHolder.cleanAll();
  }

  @Override
  public void cleanAndAdjustScope(@NotNull VcsModifiableDirtyScope scope) {
    final AbstractVcs vcs = scope.getVcs();
    if (myVcsIgnoredHolderMap.containsKey(vcs)) {
      myVcsIgnoredHolderMap.get(vcs).cleanAndAdjustScope(scope);
    }
    myIdeIgnoredFilesHolder.cleanAndAdjustScope(scope);
  }

  @Override
  public FileHolder copy() {
    final IgnoredFilesCompositeHolder result = new IgnoredFilesCompositeHolder(myProject);
    for (Map.Entry<AbstractVcs, IgnoredFilesHolder> entry : myVcsIgnoredHolderMap.entrySet()) {
      result.myVcsIgnoredHolderMap.put(entry.getKey(), (IgnoredFilesHolder)entry.getValue().copy());
    }
    result.myIdeIgnoredFilesHolder = (IgnoredFilesHolder)myIdeIgnoredFilesHolder.copy();
    return result;
  }

  @Override
  public HolderType getType() {
    return HolderType.IGNORED;
  }

  public void addFile(@NotNull VirtualFile file) {
    myIdeIgnoredFilesHolder.addFile(file);
  }

  public void addFile(@NotNull AbstractVcs vcs, @NotNull VirtualFile file) {
    myVcsIgnoredHolderMap.get(vcs).addFile(file);
  }

  public boolean isInUpdatingMode() {
    return myVcsIgnoredHolderMap.values().stream()
      .anyMatch(holder -> holder instanceof VcsIgnoredFilesHolder && ((VcsIgnoredFilesHolder)holder).isInUpdatingMode());
  }

  public boolean containsFile(@NotNull VirtualFile file) {
    if (myIdeIgnoredFilesHolder.containsFile(file)) return true;
    final AbstractVcs vcs = myVcsManager.getVcsFor(file);
    if (vcs == null) return false;
    final IgnoredFilesHolder ignoredFilesHolder = myVcsIgnoredHolderMap.get(vcs);
    return ignoredFilesHolder != null && ignoredFilesHolder.containsFile(file);
  }

  @NotNull
  public Collection<VirtualFile> values() {
    final HashSet<VirtualFile> result = ContainerUtil.newHashSet();
    result.addAll(myIdeIgnoredFilesHolder.values());
    result.addAll(StreamEx.of(myVcsIgnoredHolderMap.values()).flatCollection(IgnoredFilesHolder::values).toSet());
    return result;
  }

  @Override
  public void notifyVcsStarted(AbstractVcs vcs) {
    if (!myVcsIgnoredHolderMap.containsKey(vcs)) {
      myVcsIgnoredHolderMap.put(vcs, getHolderForVcs(myProject, vcs));
    }

    for (FileHolder fileHolder : myVcsIgnoredHolderMap.values()) {
      fileHolder.notifyVcsStarted(vcs);
    }
  }

  @NotNull
  private static IgnoredFilesHolder getHolderForVcs(@NotNull Project project, AbstractVcs vcs) {
    for (VcsIgnoredFilesHolder.Provider provider : Extensions.getExtensions(VcsIgnoredFilesHolder.VCS_IGNORED_FILES_HOLDER_EP, project)) {
      if (provider.getVcs().equals(vcs)) return provider.createHolder();
    }
    return new RecursiveFileHolder<>(project, HolderType.IGNORED);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof IgnoredFilesCompositeHolder)) {
      return false;
    }
    IgnoredFilesCompositeHolder other = (IgnoredFilesCompositeHolder)obj;
    return myVcsIgnoredHolderMap.equals(other.myVcsIgnoredHolderMap) && myIdeIgnoredFilesHolder.equals(other.myIdeIgnoredFilesHolder);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myVcsIgnoredHolderMap, myIdeIgnoredFilesHolder);
  }
}
