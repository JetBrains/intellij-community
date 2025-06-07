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

import java.util.Collections;

import static org.zmlx.hg4idea.HgNotificationIdsHolder.QGOTO_ERROR;

public class HgQGotoCommand {
  private final @NotNull HgRepository myRepository;

  public HgQGotoCommand(@NotNull HgRepository repository) {
    myRepository = repository;
  }

  public HgCommandResult executeInCurrentThread(final @NotNull String name) {
    Project project = myRepository.getProject();
    HgCommandResult result = new HgCommandExecutor(project)
      .executeInCurrentThread(myRepository.getRoot(), "qgoto", Collections.singletonList(name));
    if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
      new HgCommandResultNotifier(project)
        .notifyError(QGOTO_ERROR,
                     result,
                     HgBundle.message("action.hg4idea.QGotoFromPatches.error"),
                     HgBundle.message("action.hg4idea.QGotoFromPatches.error.msg", name));
    }
    myRepository.update();
    return result;
  }
}
