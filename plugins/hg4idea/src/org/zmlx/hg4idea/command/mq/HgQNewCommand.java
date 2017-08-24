/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zmlx.hg4idea.command.mq;

import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgCommitTypeCommand;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandExecutor;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class HgQNewCommand extends HgCommitTypeCommand {


  private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yy_MM_dd_HH-mm-ss");

  public HgQNewCommand(@NotNull Project project, @NotNull HgRepository repository, String message, boolean amend) {
    super(project, repository, message, amend);
  }

  @Override
  protected void executeChunked(@NotNull List<List<String>> chunkedCommits) throws HgCommandException, VcsException {
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
    List<String> args = ContainerUtil.newArrayList();
    args.add("-l");
    args.add(saveCommitMessage().getAbsolutePath());
    args.add("-s");
    args.addAll(chunkFiles);
    HgCommandResult result = new HgCommandExecutor(myProject).executeInCurrentThread(myRepository.getRoot(), "qrefresh", args);
    if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
      new HgCommandResultNotifier(myProject)
        .notifyError(result, "QRefresh Failed", "Could not amend selected changes to newly created patch");
    }
  }

  private void executeQNewInCurrentThread(@NotNull List<String> chunkFiles) throws VcsException {
    List<String> args = ContainerUtil.newArrayList();
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
        .notifyError(result, "Qnew Failed", "Could not create mq patch for selected changes");
    }
  }
}
