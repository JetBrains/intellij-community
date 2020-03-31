// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class IgnoredFilesCompositeHolder implements FileHolder {
  private final Map<AbstractVcs, IgnoredFilesHolder> myVcsIgnoredHolderMap;
  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;

  public IgnoredFilesCompositeHolder(final Project project) {
    super();
    myProject = project;
    myVcsIgnoredHolderMap = new HashMap<>();
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
  }

  @Override
  public void cleanAll() {
    myVcsIgnoredHolderMap.values().forEach(IgnoredFilesHolder::cleanAll);
    myVcsIgnoredHolderMap.clear();
  }

  @Override
  public void cleanAndAdjustScope(@NotNull VcsModifiableDirtyScope scope) {
    final AbstractVcs vcs = scope.getVcs();
    if (myVcsIgnoredHolderMap.containsKey(vcs)) {
      myVcsIgnoredHolderMap.get(vcs).cleanAndAdjustScope(scope);
    }
  }

  @Override
  public IgnoredFilesCompositeHolder copy() {
    final IgnoredFilesCompositeHolder result = new IgnoredFilesCompositeHolder(myProject);
    for (Map.Entry<AbstractVcs, IgnoredFilesHolder> entry : myVcsIgnoredHolderMap.entrySet()) {
      result.myVcsIgnoredHolderMap.put(entry.getKey(), (IgnoredFilesHolder)entry.getValue().copy());
    }
    return result;
  }

  public void addFile(@NotNull AbstractVcs vcs, @NotNull FilePath file) {
    myVcsIgnoredHolderMap.get(vcs).addFile(file);
  }

  public boolean isInUpdatingMode() {
    return myVcsIgnoredHolderMap.values().stream()
      .anyMatch(holder -> holder instanceof VcsIgnoredFilesHolder && ((VcsIgnoredFilesHolder)holder).isInUpdatingMode());
  }

  public boolean containsFile(@NotNull FilePath file) {
    final AbstractVcs vcs = myVcsManager.getVcsFor(file);
    if (vcs == null) return false;
    final IgnoredFilesHolder ignoredFilesHolder = myVcsIgnoredHolderMap.get(vcs);
    return ignoredFilesHolder != null && ignoredFilesHolder.containsFile(file);
  }

  @NotNull
  public Collection<FilePath> values() {
    final HashSet<FilePath> result = new HashSet<>();
    result.addAll(StreamEx.of(myVcsIgnoredHolderMap.values()).flatCollection(IgnoredFilesHolder::values).toSet());
    return result;
  }

  @Override
  public void notifyVcsStarted(@NotNull AbstractVcs vcs) {
    if (!myVcsIgnoredHolderMap.containsKey(vcs)) {
      myVcsIgnoredHolderMap.put(vcs, getHolderForVcs(myProject, vcs));
    }

    for (FileHolder fileHolder : myVcsIgnoredHolderMap.values()) {
      fileHolder.notifyVcsStarted(vcs);
    }
  }

  @NotNull
  private static IgnoredFilesHolder getHolderForVcs(@NotNull Project project, AbstractVcs vcs) {
    for (VcsIgnoredFilesHolder.Provider provider : VcsIgnoredFilesHolder.VCS_IGNORED_FILES_HOLDER_EP.getExtensions(project)) {
      if (provider.getVcs().equals(vcs)) return provider.createHolder();
    }
    return new RecursiveFileHolder(project);
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
    return myVcsIgnoredHolderMap.equals(other.myVcsIgnoredHolderMap);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myVcsIgnoredHolderMap);
  }
}
