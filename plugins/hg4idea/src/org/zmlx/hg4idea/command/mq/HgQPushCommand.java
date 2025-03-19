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

import java.util.Arrays;

import static org.zmlx.hg4idea.HgNotificationIdsHolder.QPUSH_ERROR;

public class HgQPushCommand {
  private final @NotNull HgRepository myRepository;

  public HgQPushCommand(@NotNull HgRepository repository) {
    myRepository = repository;
  }

  public void executeInCurrentThread(final @NotNull String patchName) {
    final Project project = myRepository.getProject();
    HgCommandResult result =
      new HgCommandExecutor(project).executeInCurrentThread(myRepository.getRoot(), "qpush", Arrays.asList("--move", patchName));
    if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
      new HgCommandResultNotifier(project)
        .notifyError(QPUSH_ERROR,
                     result,
                     HgBundle.message("action.hg4idea.QPushAction.error"),
                     HgBundle.message("action.hg4idea.QPushAction.error.msg", patchName));
    }
    myRepository.update();
  }
}
