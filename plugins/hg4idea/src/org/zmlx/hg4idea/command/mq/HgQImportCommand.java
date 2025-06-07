// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.command.mq;

import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.util.List;

import static org.zmlx.hg4idea.HgNotificationIdsHolder.QIMPORT_ERROR;

public class HgQImportCommand {

  private final @NotNull HgRepository myRepository;

  public HgQImportCommand(@NotNull HgRepository repository) {
    myRepository = repository;
  }

  public void execute(final @NotNull String startRevisionNumber) {
    BackgroundTaskUtil.executeOnPooledThread(myRepository, () -> executeInCurrentThread(startRevisionNumber));
  }

  public void executeInCurrentThread(final @NotNull String startRevisionNumber) {
    final Project project = myRepository.getProject();
    String lastRevisionName = myRepository.getMQAppliedPatches().isEmpty() ? "tip" : "qparent";
    List<String> arguments = List.of("--rev", startRevisionNumber + ":" + lastRevisionName);
    HgCommandResult result = new HgCommandExecutor(project).executeInCurrentThread(myRepository.getRoot(), "qimport", arguments);
    if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
      new HgCommandResultNotifier(project)
        .notifyError(QIMPORT_ERROR,
                     result,
                     HgBundle.message("action.hg4idea.QImport.error"),
                     HgBundle.message("action.hg4idea.QImport.error.msg", startRevisionNumber));
    }
    myRepository.update();
  }
}
