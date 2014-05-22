// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.command;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgPromptCommandExecutor;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.zmlx.hg4idea.util.HgErrorUtil.hasUncommittedChangesConflict;

public class HgUpdateCommand {

  private final Project project;
  private final VirtualFile repo;

  private String branch;
  private String revision;
  private boolean clean;

  public HgUpdateCommand(@NotNull Project project, @NotNull VirtualFile repo) {
    this.project = project;
    this.repo = repo;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public void setRevision(String revision) {
    this.revision = revision;
  }

  public void setClean(boolean clean) {
    this.clean = clean;
  }


  @Nullable
  public HgCommandResult execute() {
    List<String> arguments = new LinkedList<String>();
    if (clean) {
      arguments.add("--clean");
    }

    if (!StringUtil.isEmptyOrSpaces(revision)) {
      arguments.add("--rev");
      arguments.add(revision);
    } else if (!StringUtil.isEmptyOrSpaces(branch)) {
      arguments.add(branch);
    }

    final HgPromptCommandExecutor executor = new HgPromptCommandExecutor(project);
    executor.setShowOutput(true);
    HgCommandResult result =
      executor.executeInCurrentThread(repo, "update", arguments);
    if (!clean && hasUncommittedChangesConflict(result)) {
      final String message = "<html>Your uncommitted changes couldn't be merged into the requested changeset.<br>" +
                             "Would you like to perform force update and discard them?";
      if (showDiscardChangesConfirmation(project, message) == Messages.OK) {
        arguments.add("-C");
        result = executor.executeInCurrentThread(repo, "update", arguments);
      }
    }

    project.getMessageBus().syncPublisher(HgVcs.BRANCH_TOPIC).update(project, null);
    VfsUtil.markDirtyAndRefresh(true, true, false, repo);
    return result;
  }

  public static int showDiscardChangesConfirmation(@NotNull final Project project, @NotNull final String confirmationMessage) {
    final AtomicInteger exitCode = new AtomicInteger();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        exitCode.set(Messages.showOkCancelDialog(project, confirmationMessage, "Uncommitted Changes Problem",
                                                 "&Discard Changes", "&Cancel", Messages.getWarningIcon()));
      }
    });
    return exitCode.get();
  }
}
