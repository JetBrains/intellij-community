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
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgPushCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;
import org.zmlx.hg4idea.ui.HgPushDialog;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Kirill Likhodedov
 */
public class HgPusher {

  private static final Logger LOG = Logger.getInstance(HgPusher.class);
  private static Pattern PUSH_COMMITS_PATTERN = Pattern.compile(".*added (\\d+) changesets.*");
  private static Pattern PUSH_NO_CHANGES = Pattern.compile(".*no changes found.*");

  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;

  public HgPusher(Project project) {
    myProject = project;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
  }

  public void showDialogAndPush() {
    HgPushDialog dialog = new HgPushDialog(myProject);
    dialog.setRoots(HgUtil.getHgRepositories(myProject));
    dialog.show();
    if (dialog.isOK()) {
      push(myProject, dialog);
    }
  }

  private static void push(final Project project, HgPushDialog dialog) {
    final HgPushCommand command = new HgPushCommand(project, dialog.getRepository(), dialog.getTarget());
    command.setRevision(dialog.getRevision());
    command.setForce(dialog.isForce());
    command.setBranch(dialog.getBranch());
    command.execute(new HgCommandResultHandler() {
      @Override
      public void process(@Nullable HgCommandResult result) {
        int commitsNum = getNumberOfPushedCommits(result);
        String title = null;
        String description = null;
        if (commitsNum > 0) {
          title = "Pushed successfully";
          description = "Pushed " + commitsNum + " " + StringUtil.pluralize("commit", commitsNum) + ".";
        } else if (commitsNum == 0) {
          title = "";
          description = "Nothing to push";
        }
        new HgCommandResultNotifier(project).process(result, title, description);
      }
    });
  }

  private static int getNumberOfPushedCommits(HgCommandResult result) {
    if (!HgErrorUtil.isAbort(result)) {
      final List<String> outputLines = result.getOutputLines();
      for (String outputLine : outputLines) {
        outputLine = outputLine.trim();
        final Matcher matcher = PUSH_COMMITS_PATTERN.matcher(outputLine);
        if (matcher.matches()) {
          try {
            return Integer.parseInt(matcher.group(1));
          }
          catch (NumberFormatException e) {
            LOG.info("getNumberOfPushedCommits ", e);
            return -1;
          }
        } else if (PUSH_NO_CHANGES.matcher(outputLine).matches()) {
          return 0;
        }
      }
    }
    return -1;
  }

}
