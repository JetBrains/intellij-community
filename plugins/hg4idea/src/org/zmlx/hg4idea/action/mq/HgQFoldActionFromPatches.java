// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.action.mq;

import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.command.mq.HgQFoldCommand;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.List;

public class HgQFoldActionFromPatches extends HgActionFromMqPatches {

  @Override
  protected void executeInCurrentThread(@NotNull HgRepository repository, @NotNull List<String> patchNames) {
    new HgQFoldCommand(repository).executeInCurrentThread(patchNames);
  }

  @Override
  protected @NotNull String getTitle() {
    return HgBundle.message("action.hg4idea.QFold.title");
  }

  @Override
  protected boolean isEnabled(@NotNull HgRepository repository) {
    return !repository.getMQAppliedPatches().isEmpty();
  }
}
