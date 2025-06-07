// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.command.mq;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.util.List;

import static org.zmlx.hg4idea.HgNotificationIdsHolder.QFOLD_ERROR;

public class HgQFoldCommand {
  private final @NotNull HgRepository myRepository;

  public HgQFoldCommand(@NotNull HgRepository repository) {
    myRepository = repository;
  }

  public void executeInCurrentThread(final @NotNull List<String> patchNames) {
    final Project project = myRepository.getProject();
    HgCommandResult result = new HgCommandExecutor(project)
      .executeInCurrentThread(myRepository.getRoot(), "qfold", patchNames);
    if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
      new HgCommandResultNotifier(project)
        .notifyError(QFOLD_ERROR,
                     result,
                     HgBundle.message("action.hg4idea.QFold.error"),
                     HgBundle.message("action.hg4idea.QFold.error.msg"));
    }
    myRepository.update();
  }
}
