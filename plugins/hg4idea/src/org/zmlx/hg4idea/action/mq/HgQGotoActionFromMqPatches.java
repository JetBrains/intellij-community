// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.action.mq;

import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.command.mq.HgQGotoCommand;
import org.zmlx.hg4idea.repo.HgRepository;

public class HgQGotoActionFromMqPatches extends HgSingleActionFomMqPatches {

  @Override
  protected void executeInCurrentThread(@NotNull HgRepository repository, @NotNull String patchName) {
    new HgQGotoCommand(repository).executeInCurrentThread(patchName);
  }

  @Override
  protected @NotNull String getTitle() {
    return HgBundle.message("action.hg4idea.QGotoFromPatches.title");
  }
}
