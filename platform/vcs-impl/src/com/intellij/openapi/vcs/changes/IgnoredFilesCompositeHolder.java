// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class IgnoredFilesCompositeHolder implements FileHolder {
  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;

  private final Map<AbstractVcs, FilePathHolder> myVcsIgnoredHolderMap = new HashMap<>();

  public IgnoredFilesCompositeHolder(@NotNull Project project) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
  }

  @Override
  public void cleanAll() {
    myVcsIgnoredHolderMap.values().forEach(FilePathHolder::cleanAll);
    myVcsIgnoredHolderMap.clear();
  }

  @Override
  public void cleanAndAdjustScope(@NotNull VcsModifiableDirtyScope scope) {
    AbstractVcs vcs = scope.getVcs();
    FilePathHolder ignoredFilesHolder = myVcsIgnoredHolderMap.get(vcs);
    if (ignoredFilesHolder != null) {
      ignoredFilesHolder.cleanAndAdjustScope(scope);
    }
  }

  @Override
  public IgnoredFilesCompositeHolder copy() {
    IgnoredFilesCompositeHolder result = new IgnoredFilesCompositeHolder(myProject);
    for (Map.Entry<AbstractVcs, FilePathHolder> entry : myVcsIgnoredHolderMap.entrySet()) {
      result.myVcsIgnoredHolderMap.put(entry.getKey(), (FilePathHolder)entry.getValue().copy());
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
    AbstractVcs vcs = myVcsManager.getVcsFor(file);
    if (vcs == null) return false;
    FilePathHolder ignoredFilesHolder = myVcsIgnoredHolderMap.get(vcs);
    return ignoredFilesHolder != null && ignoredFilesHolder.containsFile(file);
  }

  @NotNull
  public Collection<FilePath> values() {
    HashSet<FilePath> result = new HashSet<>();
    for (FilePathHolder fileHolder : myVcsIgnoredHolderMap.values()) {
      result.addAll(fileHolder.values());
    }
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
  private static FilePathHolder getHolderForVcs(@NotNull Project project, AbstractVcs vcs) {
    VcsIgnoredFilesHolder.Provider provider = VcsIgnoredFilesHolder.VCS_IGNORED_FILES_HOLDER_EP
      .findFirstSafe(project, ep -> ep.getVcs().equals(vcs));
    if (provider != null) {
      return provider.createHolder();
    }
    else {
      return new RecursiveFilePathHolderImpl(project);
    }
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
