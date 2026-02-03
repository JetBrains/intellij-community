// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.command.mq;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.HgNameWithHashInfo;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgPatchReferenceValidator;

import java.util.Arrays;

import static org.zmlx.hg4idea.HgNotificationIdsHolder.QRENAME_ERROR;

public class HgQRenameCommand {

  private static final Logger LOG = Logger.getInstance(HgQRenameCommand.class);
  private final @NotNull HgRepository myRepository;

  public HgQRenameCommand(@NotNull HgRepository repository) {
    myRepository = repository;
  }

  public void execute(final @NotNull Hash patchHash) {
    final Project project = myRepository.getProject();
    HgNameWithHashInfo patchInfo = ContainerUtil.find(myRepository.getMQAppliedPatches(), info -> info.getHash().equals(patchHash));
    if (patchInfo == null) {
      LOG.error("Could not find patch " + patchHash.toString());
      return;
    }
    final String oldName = patchInfo.getName();
    final String newName = Messages.showInputDialog(project, HgBundle.message("action.hg4idea.QRename.enter.patch.name", oldName),
                                                    HgBundle.message("action.hg4idea.QRename.title"), Messages.getQuestionIcon(), "", new HgPatchReferenceValidator(
        myRepository));
    if (newName != null) {
      performPatchRename(myRepository, oldName, newName);
    }
  }

  public static void performPatchRename(@NotNull HgRepository repository,
                                        @NotNull String oldName,
                                        @NotNull String newName) {
    if (oldName.equals(newName)) return;
    Project project = repository.getProject();
    BackgroundTaskUtil.executeOnPooledThread(repository, () -> {
      HgCommandExecutor executor = new HgCommandExecutor(project);
      HgCommandResult result = executor.executeInCurrentThread(repository.getRoot(), "qrename", Arrays.asList(oldName, newName));
      if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
        new HgCommandResultNotifier(project).notifyError(QRENAME_ERROR,
                                                         result,
                                                         HgBundle.message("action.hg4idea.QRename.error"),
                                                         HgBundle.message("action.hg4idea.QRename.error.msg", oldName, newName));
      }
      repository.update();
    });
  }
}
