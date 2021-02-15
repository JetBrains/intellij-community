// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.command.mq;

import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgCommitTypeCommand;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.zmlx.hg4idea.HgNotificationIdsHolder.QNEW_ERROR;
import static org.zmlx.hg4idea.HgNotificationIdsHolder.QREFRESH_ERROR;

public class HgQNewCommand extends HgCommitTypeCommand {


  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yy_MM_dd_HH-mm-ss");

  public HgQNewCommand(@NotNull Project project, @NotNull HgRepository repository, String message, boolean amend) {
    super(project, repository, message, amend);
  }

  @Override
  protected void executeChunked(@NotNull List<List<String>> chunkedCommits) throws VcsException {
    if (chunkedCommits.isEmpty()) {
      executeQNewInCurrentThread(ContainerUtil.emptyList());
    }
    else {
      int size = chunkedCommits.size();
      int i = 0;
      if (!myAmend) {
        executeQNewInCurrentThread(chunkedCommits.get(0));
        i = 1;
      }
      for (; i < size; i++) {
        executeQRefreshInCurrentThread(chunkedCommits.get(i));
      }
    }
    myRepository.update();
    BackgroundTaskUtil.syncPublisher(myProject, HgVcs.REMOTE_TOPIC).update(myProject, null);
  }

  private void executeQRefreshInCurrentThread(@NotNull List<String> chunkFiles) throws VcsException {
    List<String> args = new ArrayList<>();
    args.add("-l");
    args.add(saveCommitMessage().getAbsolutePath());
    args.add("-s");
    args.addAll(chunkFiles);
    HgCommandResult result = new HgCommandExecutor(myProject).executeInCurrentThread(myRepository.getRoot(), "qrefresh", args);
    if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
      new HgCommandResultNotifier(myProject)
        .notifyError(QREFRESH_ERROR,
                     result,
                     HgBundle.message("action.hg4idea.QRefresh.error"),
                     HgBundle.message("action.hg4idea.QRefresh.error.msg"));
    }
  }

  private void executeQNewInCurrentThread(@NotNull List<String> chunkFiles) throws VcsException {
    List<String> args = new ArrayList<>();
    args.add("-l");
    args.add(saveCommitMessage().getAbsolutePath());
    args.add("-UD");
    String patchName = DATE_FORMAT.format(new Date()).concat(".diff");
    args.add(patchName);
    args.addAll(chunkFiles);
    HgCommandExecutor executor = new HgCommandExecutor(myProject);
    HgCommandResult result = executor.executeInCurrentThread(myRepository.getRoot(), "qnew", args);
    if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
      new HgCommandResultNotifier(myProject)
        .notifyError(QNEW_ERROR,
                     result,
                     HgBundle.message("action.hg4idea.QNew.error"),
                     HgBundle.message("action.hg4idea.QNew.error.msg"));
    }
  }
}
