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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgPushCommand;
import org.zmlx.hg4idea.command.HgShowConfigCommand;
import org.zmlx.hg4idea.command.HgTagBranch;
import org.zmlx.hg4idea.command.HgTagBranchCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;
import org.zmlx.hg4idea.ui.HgPushDialog;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Kirill Likhodedov
 */
public class HgPusher {

  private static final Logger LOG = Logger.getInstance(HgPusher.class);
  private static Pattern PUSH_COMMITS_PATTERN = Pattern.compile(".*added (\\d+) changesets.*");

  private final Project myProject;

  public HgPusher(Project project) {
    myProject = project;
  }

  public void showDialogAndPush() {
    HgUtil.executeOnPooledThreadIfNeeded(new Runnable() {
      public void run() {
        final List<VirtualFile> repositories = HgUtil.getHgRepositories(myProject);
        if (repositories.isEmpty()) {
          VcsBalloonProblemNotifier.showOverChangesView(myProject, "No Mercurial repositories in the project", MessageType.ERROR);
          return;
        }
        VirtualFile firstRepo = repositories.get(0);
        final List<HgTagBranch> branches = getBranches(myProject, firstRepo);

        final AtomicReference<HgPushCommand> pushCommand = new AtomicReference<HgPushCommand>();
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            final HgPushDialog dialog = new HgPushDialog(myProject, repositories, branches);
            dialog.show();
            if (dialog.isOK()) {
              dialog.rememberSettings();
              pushCommand.set(preparePushCommand(myProject, dialog));
            }
          }
        });

        if (pushCommand.get() != null) {
          push(myProject, pushCommand.get());
        }
      }
    });
  }

  public static String getDefaultPushPath(@NotNull Project project, @NotNull VirtualFile repo) {
    final HgShowConfigCommand configCommand = new HgShowConfigCommand(project);
    return configCommand.getDefaultPushPath(repo);
  }

  @NotNull
  public static List<HgTagBranch> getBranches(@NotNull Project project, @NotNull VirtualFile root) {
    final List<HgTagBranch> branchesList = new ArrayList<HgTagBranch>();
    new HgTagBranchCommand(project, root).listBranches(new Consumer<List<HgTagBranch>>() {
      @Override
      public void consume(final List<HgTagBranch> branches) {
        branchesList.addAll(branches);
      }
    });
    return branchesList;
  }

  private static void push(final Project project, HgPushCommand command) {
    final VirtualFile repo = command.getRepo();
    command.execute(new HgCommandResultHandler() {
      @Override
      public void process(@Nullable HgCommandResult result) {
        if (result == null) {
          return;
        }

        int commitsNum = getNumberOfPushedCommits(result);
        if (commitsNum > 0 && result.getExitValue() == 0) {
          String successTitle = "Pushed successfully";
          String successDescription = String.format("Pushed %d %s [%s]", commitsNum, StringUtil.pluralize("commit", commitsNum),
                                                    repo.getPresentableName());
          new HgCommandResultNotifier(project).notifySuccess(successTitle, successDescription);
        } else if (commitsNum == 0) {
          new HgCommandResultNotifier(project).notifySuccess("", "Nothing to push");
        } else {
          new HgCommandResultNotifier(project).notifyError(result, "Push failed",
                                                           "Failed to push to [" + repo.getPresentableName() + "]");
        }
      }
    });
  }

  private static HgPushCommand preparePushCommand(Project project, HgPushDialog dialog) {
    final HgPushCommand command = new HgPushCommand(project, dialog.getRepository(), dialog.getTarget());
    command.setRevision(dialog.getRevision());
    command.setForce(dialog.isForce());
    command.setBranch(dialog.getBranch());
    return command;
  }

  private static int getNumberOfPushedCommits(HgCommandResult result) {
    int numberOfCommitsInAllSubrepos = 0;
    if (!HgErrorUtil.isAbort(result)) {
      final List<String> outputLines = result.getOutputLines();
      for (String outputLine : outputLines) {
        outputLine = outputLine.trim();
        final Matcher matcher = PUSH_COMMITS_PATTERN.matcher(outputLine);
        if (matcher.matches()) {
          try {
            numberOfCommitsInAllSubrepos += Integer.parseInt(matcher.group(1));
          }
          catch (NumberFormatException e) {
            LOG.info("getNumberOfPushedCommits ", e);
            return -1;
          }
        }
      }
      return numberOfCommitsInAllSubrepos;
    }
    return -1;
  }

}
