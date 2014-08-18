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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgPushCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HgPusher {

  private static final Logger LOG = Logger.getInstance(HgPusher.class);
  private static final String ONE = "one";
  private static Pattern PUSH_COMMITS_PATTERN = Pattern.compile(".*(?:added|pushed) (\\d+|" + ONE + ") changeset.*");
  // hg push command has definite exit values for some cases:
  // mercurial returns 0 if push was successful, 1 if nothing to push. see hg push --help
  private static int PUSH_SUCCEEDED_EXIT_VALUE = 0;
  private static int NOTHING_TO_PUSH_EXIT_VALUE = 1;

  public static void push(final Project project, HgPushCommand command) {
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
    });
  }

  private static int getNumberOfPushedCommits(@NotNull HgCommandResult result) {
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
