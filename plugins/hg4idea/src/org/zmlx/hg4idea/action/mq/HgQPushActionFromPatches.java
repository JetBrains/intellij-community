// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.action.mq;

import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.command.mq.HgQPushCommand;
import org.zmlx.hg4idea.repo.HgRepository;

public class HgQPushActionFromPatches extends HgSingleActionFomMqPatches {
  @Override
  protected void executeInCurrentThread(@NotNull HgRepository repository, @NotNull String patchName) {
    new HgQPushCommand(repository).executeInCurrentThread(patchName);
  }

  @Override
  protected @NotNull String getTitle() {
    return HgBundle.message("action.hg4idea.QPushAction.title");
  }
}
