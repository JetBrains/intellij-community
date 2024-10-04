// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@ApiStatus.Internal
public abstract class CompositeFilePathHolder implements FileHolder {
  protected final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;

  private final Map<AbstractVcs, FilePathHolder> myMap = new HashMap<>();

  public CompositeFilePathHolder(@NotNull Project project) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(myProject);
  }

  @Override
  public void cleanAll() {
    myMap.values().forEach(FilePathHolder::cleanAll);
    myMap.clear();
  }

  @Override
  public void cleanUnderScope(@NotNull VcsDirtyScope scope) {
    AbstractVcs vcs = scope.getVcs();
    FilePathHolder holder = myMap.get(vcs);
    if (holder != null) {
      holder.cleanUnderScope(scope);
    }
  }

  protected void copyFrom(@NotNull CompositeFilePathHolder holder) {
    for (Map.Entry<AbstractVcs, FilePathHolder> entry : holder.myMap.entrySet()) {
      myMap.put(entry.getKey(), (FilePathHolder)entry.getValue().copy());
    }
  }

  public void addFile(@NotNull AbstractVcs vcs, @NotNull FilePath file) {
    myMap.get(vcs).addFile(file);
  }

  public boolean isInUpdatingMode() {
    return myMap.values().stream()
      .anyMatch(holder -> holder instanceof VcsManagedFilesHolder && ((VcsManagedFilesHolder)holder).isInUpdatingMode());
  }

  public boolean containsFile(@NotNull FilePath file, @NotNull VcsRoot vcsRoot) {
    FilePathHolder holder = myMap.get(vcsRoot.getVcs());
    return holder != null && holder.containsFile(file, vcsRoot.getPath());
  }

  @NotNull
  public Collection<FilePath> getFiles() {
    HashSet<FilePath> result = new HashSet<>();
    for (FilePathHolder fileHolder : myMap.values()) {
      result.addAll(fileHolder.values());
    }
    return result;
  }

  @Override
  public void notifyVcsStarted(@NotNull AbstractVcs vcs) {
    if (!myMap.containsKey(vcs)) {
      myMap.put(vcs, createHolderForVcs(myProject, vcs));
    }

    for (FileHolder fileHolder : myMap.values()) {
      fileHolder.notifyVcsStarted(vcs);
    }
  }

  @NotNull
  protected abstract FilePathHolder createHolderForVcs(@NotNull Project project, @NotNull AbstractVcs vcs);

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CompositeFilePathHolder holder = (CompositeFilePathHolder)o;
    return Objects.equals(myMap, holder.myMap);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myMap);
  }

  public static class UnversionedFilesCompositeHolder extends CompositeFilePathHolder {
    public UnversionedFilesCompositeHolder(@NotNull Project project) {
      super(project);
    }

    @NotNull
    @Override
    protected FilePathHolder createHolderForVcs(@NotNull Project project, @NotNull AbstractVcs vcs) {
      VcsManagedFilesHolder.Provider provider = VcsManagedFilesHolder.VCS_UNVERSIONED_FILES_HOLDER_EP
        .findFirstSafe(project, ep -> ep.getVcs().equals(vcs));
      if (provider != null) {
        return provider.createHolder();
      }
      else {
        return new FilePathHolderImpl(project);
      }
    }

    @Override
    public UnversionedFilesCompositeHolder copy() {
      UnversionedFilesCompositeHolder result = new UnversionedFilesCompositeHolder(myProject);
      result.copyFrom(this);
      return result;
    }
  }

  public static class IgnoredFilesCompositeHolder extends CompositeFilePathHolder {
    public IgnoredFilesCompositeHolder(@NotNull Project project) {
      super(project);
    }

    @NotNull
    @Override
    protected FilePathHolder createHolderForVcs(@NotNull Project project, @NotNull AbstractVcs vcs) {
      VcsManagedFilesHolder.Provider provider = VcsManagedFilesHolder.VCS_IGNORED_FILES_HOLDER_EP
        .findFirstSafe(project, ep -> ep.getVcs().equals(vcs));
      if (provider != null) {
        return provider.createHolder();
      }
      else {
        return new RecursiveFilePathHolderImpl(project);
      }
    }

    @Override
    public IgnoredFilesCompositeHolder copy() {
      IgnoredFilesCompositeHolder result = new IgnoredFilesCompositeHolder(myProject);
      result.copyFrom(this);
      return result;
    }
  }
}
