// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider;

import com.intellij.dvcs.ignore.VcsIgnoredFilesHolderBase;
import com.intellij.dvcs.ignore.VcsRepositoryIgnoredFilesHolder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.ChangesViewRefresher;
import com.intellij.openapi.vcs.changes.VcsManagedFilesHolder;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;
import org.zmlx.hg4idea.util.HgUtil;

public class HgIgnoredFileHolder extends VcsIgnoredFilesHolderBase<HgRepository> {
  public HgIgnoredFileHolder(@NotNull HgRepositoryManager manager) {
    super(manager);
  }

  @NotNull
  @Override
  protected VcsRepositoryIgnoredFilesHolder getHolder(@NotNull HgRepository repository) {
    return repository.getIgnoredFilesHolder();
  }

  public static class Provider implements VcsManagedFilesHolder.Provider, ChangesViewRefresher {
    private final HgVcs myVcs;
    private final HgRepositoryManager myManager;

    public Provider(Project project) {
      myVcs = HgVcs.getInstance(project);
      myManager = HgUtil.getRepositoryManager(project);
    }

    @NotNull
    @Override
    public AbstractVcs getVcs() {
      return myVcs;
    }

    @NotNull
    @Override
    public VcsManagedFilesHolder createHolder() {
      return new HgIgnoredFileHolder(myManager);
    }

    @Override
    public void refresh(@NotNull Project project) {
      myManager.getRepositories().forEach(r -> r.getIgnoredFilesHolder().startRescan());
    }
  }
}
