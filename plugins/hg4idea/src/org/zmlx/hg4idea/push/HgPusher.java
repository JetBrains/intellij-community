/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.zmlx.hg4idea.push;

import com.intellij.dvcs.push.PushSpec;
import com.intellij.dvcs.push.Pusher;
import com.intellij.dvcs.push.VcsPushOptionValue;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgPushCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HgPusher extends Pusher<HgRepository, HgPushSource, HgTarget> {

  private static final Logger LOG = Logger.getInstance(HgPusher.class);
  private static final String ONE = "one";
  private static final Pattern PUSH_COMMITS_PATTERN = Pattern.compile(".*(?:added|pushed) (\\d+|" + ONE + ") changeset.*");
  // hg push command has definite exit values for some cases:
  // mercurial returns 0 if push was successful, 1 if nothing to push. see hg push --help
  static int PUSH_SUCCEEDED_EXIT_VALUE = 0;
  static int NOTHING_TO_PUSH_EXIT_VALUE = 1;

  @Override
  public void push(@NotNull Map<HgRepository, PushSpec<HgPushSource, HgTarget>> pushSpecs,
                   @Nullable VcsPushOptionValue vcsPushOptionValue, boolean force) {
    for (Map.Entry<HgRepository, PushSpec<HgPushSource, HgTarget>> entry : pushSpecs.entrySet()) {
      HgRepository repository = entry.getKey();
      PushSpec<HgPushSource, HgTarget> hgSpec = entry.getValue();
      HgTarget destination = hgSpec.getTarget();
      HgPushSource source = hgSpec.getSource();
      Project project = repository.getProject();
      final HgPushCommand pushCommand = new HgPushCommand(project, repository.getRoot(), destination.myTarget);
      pushCommand.setIsNewBranch(true); // set always true, because it just allow mercurial to create a new one if needed
      pushCommand.setForce(force);
      String branchName = source.getBranch();
      if (branchName.equals(repository.getCurrentBookmark())) {
        if (vcsPushOptionValue == HgVcsPushOptionValue.Current) {
          pushCommand.setBookmarkName(branchName);
        }
        else {
          pushCommand.setRevision(branchName);
        }
      }
      else {
        pushCommand.setBranchName(branchName);
      }
      pushSynchronously(project, pushCommand);
    }
  }

  public static void pushSynchronously(@NotNull final Project project, @NotNull HgPushCommand command) {
    final VirtualFile repo = command.getRepo();
    HgCommandResult result = command.executeInCurrentThread();
    if (result == null) {
      return;
    }

    if (result.getExitValue() == PUSH_SUCCEEDED_EXIT_VALUE) {
      int commitsNum = getNumberOfPushedCommits(result);
      String successTitle = "Pushed successfully";
      String successDescription = String.format("Pushed %d %s [%s]", commitsNum, StringUtil.pluralize("commit", commitsNum),
                                                repo.getPresentableName());
      VcsNotifier.getInstance(project).notifySuccess(successTitle, successDescription);
    }
    else if (result.getExitValue() == NOTHING_TO_PUSH_EXIT_VALUE) {
      VcsNotifier.getInstance(project).notifySuccess("Nothing to push");
    }
    else {
      new HgCommandResultNotifier(project).notifyError(result, "Push failed",
                                                       "Failed to push to [" + repo.getPresentableName() + "]");
    }
  }

  static int getNumberOfPushedCommits(@NotNull HgCommandResult result) {
    int numberOfCommitsInAllSubrepos = 0;
    final List<String> outputLines = result.getOutputLines();
    for (String outputLine : outputLines) {
      outputLine = outputLine.trim();
      final Matcher matcher = PUSH_COMMITS_PATTERN.matcher(outputLine);
      if (matcher.matches()) {
        try {
          String numberOfCommits = matcher.group(1);
          numberOfCommitsInAllSubrepos += ONE.equals(numberOfCommits) ? 1 : Integer.parseInt(numberOfCommits);
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
