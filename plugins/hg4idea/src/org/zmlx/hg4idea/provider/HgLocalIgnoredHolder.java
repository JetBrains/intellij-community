// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.provider;

import com.intellij.dvcs.ignore.VcsRepositoryIgnoredFilesHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.command.HgStatusCommand;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.repo.HgRepositoryManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class HgLocalIgnoredHolder extends VcsRepositoryIgnoredFilesHolderBase<HgRepository> {

  public HgLocalIgnoredHolder(@NotNull HgRepository repository, @NotNull HgRepositoryManager repositoryManager) {
    super(repository, repositoryManager);
  }

  @NotNull
  @Override
  protected Set<FilePath> requestIgnored(@Nullable Collection<? extends FilePath> paths) throws VcsException {
    Set<FilePath> ignored = new HashSet<>();
    ignored.addAll(new HgStatusCommand.Builder(false).ignored(true).build(repository.getProject())
                     .getFilePaths(repository.getRoot(), paths != null ? new ArrayList<>(paths) : null));
    return ignored;
  }

  @Override
  protected boolean scanTurnedOff() {
    return !Registry.is("hg4idea.process.ignored");
  }
}
