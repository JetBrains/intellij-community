// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.command.mq;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsNotifier;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.util.Collections;

import static org.zmlx.hg4idea.HgNotificationIdsHolder.QPOP_COMPLETED_WITH_ERRORS;
import static org.zmlx.hg4idea.HgNotificationIdsHolder.QPOP_ERROR;

public class HgQPopCommand {
  private final @NotNull HgRepository myRepository;

  public HgQPopCommand(@NotNull HgRepository repository) {
    myRepository = repository;
  }

  public HgCommandResult executeInCurrentThread() {
    final Project project = myRepository.getProject();
    HgCommandResult result = new HgCommandExecutor(project)
      .executeInCurrentThread(myRepository.getRoot(), "qpop", Collections.singletonList("--all"));
    if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
      new HgCommandResultNotifier(project)
        .notifyError(QPOP_ERROR,
                     result,
                     HgBundle.message("action.hg4idea.QPop.error"),
                     HgBundle.message("action.hg4idea.QPop.error.msg"));
    }
    else {
      assert result != null;
      if (!result.getErrorLines().isEmpty()) {
        VcsNotifier.getInstance(project).notifyWarning(QPOP_COMPLETED_WITH_ERRORS,
                                                       HgBundle.message("action.hg4idea.QPop.error.warning"),
                                                       result.getRawError());
      }
    }
    myRepository.update();
    return result;
  }
}
