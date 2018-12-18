// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider;

import com.intellij.dvcs.ignore.VcsIgnoredFilesHolderBase;
import com.intellij.dvcs.ignore.VcsRepositoryIgnoredFilesHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.ChangesViewRefresher;
import com.intellij.openapi.vcs.changes.FileHolder;
import com.intellij.openapi.vcs.changes.VcsIgnoredFilesHolder;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgUtil;

public class HgIgnoredFileHolder extends VcsIgnoredFilesHolderBase<HgRepository> {
  private final Project myProject;
  private final HgRepositoryManager myManager;

  public HgIgnoredFileHolder(@NotNull Project project, @NotNull HgRepositoryManager manager) {
    super(manager);
    myProject = project;
    myManager = manager;
  }

  @NotNull
  @Override
  protected VcsRepositoryIgnoredFilesHolder getHolder(@NotNull HgRepository repository) {
    return repository.getIgnoredFilesHolder();
  }

  @Override
  public FileHolder copy() {
    return new HgIgnoredFileHolder(myProject, myManager); // re-scan roots on refresh
  }

  public static class Provider implements VcsIgnoredFilesHolder.Provider, ChangesViewRefresher {
    private final Project myProject;
    private final HgVcs myVcs;
    private final HgRepositoryManager myManager;

    public Provider(Project project) {
      myProject = project;
      myVcs = HgVcs.getInstance(myProject);
      myManager = HgUtil.getRepositoryManager(myProject);
    }

    @NotNull
    @Override
    public AbstractVcs getVcs() {
      return myVcs;
    }

    @NotNull
    @Override
    public VcsIgnoredFilesHolder createHolder() {
      return new HgIgnoredFileHolder(myProject, myManager);
    }

    @Override
    public void refresh(Project project) {
      myManager.getRepositories().forEach(r -> r.getIgnoredFilesHolder().startRescan());
    }
  }
}
