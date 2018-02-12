// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.ChangesViewRefresher;
import com.intellij.openapi.vcs.changes.FileHolder;
import com.intellij.openapi.vcs.changes.VcsIgnoredFilesHolder;
import com.intellij.openapi.vcs.changes.VcsModifiableDirtyScope;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class HgIgnoredFileHolder implements VcsIgnoredFilesHolder {
  private final Project myProject;
  private final Map<HgRepository, HgLocalIgnoredHolder> myVcsIgnoredHolderMap;

  public HgIgnoredFileHolder(Project project) {
    myProject = project;
    myVcsIgnoredHolderMap = ContainerUtil.newHashMap();
    for (HgRepository repository : HgUtil.getRepositoryManager(myProject).getRepositories()) {
      myVcsIgnoredHolderMap.put(repository, repository.getLocalIgnoredHolder());
    }
  }

  @Override
  public void addFile(@NotNull VirtualFile file) {
  }

  @Override
  public boolean containsFile(@NotNull VirtualFile file) {
    HgRepository repositoryForFile = HgUtil.getRepositoryForFile(myProject, file);
    if (repositoryForFile == null) return false;
    HgLocalIgnoredHolder localIgnoredHolder = myVcsIgnoredHolderMap.get(repositoryForFile);
    return localIgnoredHolder != null && localIgnoredHolder.contains(file);
  }

  @NotNull
  @Override
  public Collection<VirtualFile> values() {
    return myVcsIgnoredHolderMap.values().stream().map(HgLocalIgnoredHolder::getIgnoredFiles).flatMap(Set::stream)
      .collect(Collectors.toSet());
  }

  @Override
  public void cleanAndAdjustScope(@NotNull final VcsModifiableDirtyScope scope) {
  }

  @Override
  public void cleanAll() {
    myVcsIgnoredHolderMap.clear();
  }

  @Override
  public FileHolder copy() {
    return new HgIgnoredFileHolder(myProject); // re-scan roots on refresh
  }

  @Override
  public HolderType getType() {
    return HolderType.IGNORED;
  }

  @Override
  public void notifyVcsStarted(AbstractVcs scope) {
  }

  @Override
  public boolean isInUpdatingMode() {
    return myVcsIgnoredHolderMap.values().stream().anyMatch(HgLocalIgnoredHolder::isInUpdateMode);
  }

  public static class Provider implements VcsIgnoredFilesHolder.Provider, ChangesViewRefresher {
    private final Project myProject;
    private final HgVcs myVcs;

    public Provider(Project project) {
      myProject = project;
      myVcs = HgVcs.getInstance(myProject);
    }

    @NotNull
    @Override
    public AbstractVcs getVcs() {
      return myVcs;
    }

    @NotNull
    @Override
    public VcsIgnoredFilesHolder createHolder() {
      return new HgIgnoredFileHolder(myProject);
    }

    @Override
    public void refresh(Project project) {
      HgUtil.getRepositoryManager(project).getRepositories().forEach(r -> r.getLocalIgnoredHolder().startRescan());
    }
  }
}
