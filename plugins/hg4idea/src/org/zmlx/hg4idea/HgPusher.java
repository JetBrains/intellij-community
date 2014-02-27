/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.zmlx.hg4idea;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgPushCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.ui.HgPushDialog;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HgPusher {

  private static final Logger LOG = Logger.getInstance(HgPusher.class);
  private static Pattern PUSH_COMMITS_PATTERN = Pattern.compile(".*added (\\d+) changesets.*");
  // hg push command has definite exit values for some cases:
  // mercurial returns 0 if push was successful, 1 if nothing to push. see hg push --help
  private static int PUSH_SUCCEEDED_EXIT_VALUE = 0;
  private static int NOTHING_TO_PUSH_EXIT_VALUE = 1;

  private final Project myProject;

  public HgPusher(Project project) {
    myProject = project;
  }

  public void showDialogAndPush (@NotNull Collection<HgRepository> repositories,@Nullable final HgRepository selectedRepo) {

    if (repositories.isEmpty()) {
      VcsBalloonProblemNotifier.showOverChangesView(myProject, "No Mercurial repositories in the project", MessageType.ERROR);
      return;
    }
    final AtomicReference<HgPushCommand> pushCommand = new AtomicReference<HgPushCommand>();
    final HgPushDialog dialog = new HgPushDialog(myProject, repositories, selectedRepo);
    dialog.show();
    if (dialog.isOK()) {
      dialog.rememberSettings();
      pushCommand.set(preparePushCommand(myProject, dialog));
      new Task.Backgroundable(myProject, "Pushing...", false) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          if (pushCommand.get() != null) {
            push(myProject, pushCommand.get());
          }
        }
      }.queue();
    }
  }

  private static void push(final Project project, HgPushCommand command) {
    final VirtualFile repo = command.getRepo();
    command.execute(new HgCommandResultHandler() {
      @Override
      public void process(@Nullable HgCommandResult result) {
        if (result == null) {
          return;
        }

        if (result.getExitValue() == PUSH_SUCCEEDED_EXIT_VALUE) {
          int commitsNum = getNumberOfPushedCommits(result);
          String successTitle = "Pushed successfully";
          String successDescription = String.format("Pushed %d %s [%s]", commitsNum, StringUtil.pluralize("commit", commitsNum),
                                                    repo.getPresentableName());
          new HgCommandResultNotifier(project).notifySuccess(successTitle, successDescription);
        }
        else if (result.getExitValue() == NOTHING_TO_PUSH_EXIT_VALUE) {
          new HgCommandResultNotifier(project).notifySuccess("", "Nothing to push");
        }
        else {
          new HgCommandResultNotifier(project).notifyError(result, "Push failed",
                                                           "Failed to push to [" + repo.getPresentableName() + "]");
        }
      }
    });
  }

  private static HgPushCommand preparePushCommand(Project project, HgPushDialog dialog) {
    final HgPushCommand command = new HgPushCommand(project, dialog.getRepository().getRoot(), dialog.getTarget());
    command.setRevision(dialog.getRevision());
    command.setForce(dialog.isForce());
    command.setBranchName(dialog.getBranch());
    command.setBookmarkName(dialog.getBookmarkName());
    command.setIsNewBranch(dialog.isNewBranch());
    return command;
  }

  private static int getNumberOfPushedCommits(HgCommandResult result) {
    int numberOfCommitsInAllSubrepos = 0;
    final List<String> outputLines = result.getOutputLines();
    for (String outputLine : outputLines) {
      outputLine = outputLine.trim();
      final Matcher matcher = PUSH_COMMITS_PATTERN.matcher(outputLine);
      if (matcher.matches()) {
        try {
          numberOfCommitsInAllSubrepos += Integer.parseInt(matcher.group(1));
        }
        catch (NumberFormatException e) {
          LOG.error("getNumberOfPushedCommits ", e);
          return -1;
        }
      }
    }
    return numberOfCommitsInAllSubrepos;
  }
}
