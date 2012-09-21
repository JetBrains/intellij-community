// Copyright Robin Stevens
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

public class HgPusher {

  private static final Logger LOG = Logger.getInstance(HgPusher.class);
  private static Pattern PUSH_COMMITS_PATTERN = Pattern.compile(".*added (\\d+) changesets.*");
  private static Pattern PUSH_NO_CHANGES = Pattern.compile(".*no changes found.*");

  private final Project myProject;

  public HgPusher(Project project) {
    this.myProject = project;
  }

  public void showDialogAndPush(){
    HgUtil.executeOnPooledThreadIfNeeded(new Runnable() {
      @Override
      public void run() {
        final List<VirtualFile> repositories = HgUtil.getHgRepositories(myProject);
        if (repositories.isEmpty()) {
          VcsBalloonProblemNotifier.showOverChangesView(myProject, "No Mercurial repositories in the project", MessageType.ERROR);
        }
        final List<List<HgTagBranch>> branches = new ArrayList<List<HgTagBranch>>(repositories.size());
        for (VirtualFile repo : repositories) {
          branches.add(getBranches(myProject, repo));
        }
        final VirtualFile firstRepo = repositories.get(0);

        final AtomicReference<List<HgPushCommand>> pushCommands = new AtomicReference<List<HgPushCommand>>();
        UIUtil.invokeAndWaitIfNeeded(new Runnable() {
          @Override
          public void run() {
            final HgPushDialog dialog = new HgPushDialog(myProject, repositories, branches, firstRepo);
            dialog.show();
            if (dialog.isOK()) {
              pushCommands.set(preparePushCommands(myProject, dialog));
            }
          }
        });

        List<HgPushCommand> commands = pushCommands.get();
        //only when commands != null, the OK button has been pressed
        if (commands != null) {
          pushAll(myProject, commands);
        }
      }
    });
  }

  public static String getDefaultPushPath(@NotNull Project project, @NotNull VirtualFile repo) {
    final HgShowConfigCommand configCommand = new HgShowConfigCommand(project);
    return configCommand.getDefaultPushPath(repo);
  }

  public static List<HgTagBranch> getBranches(@NotNull Project project, @NotNull VirtualFile root) {
    final AtomicReference<List<HgTagBranch>> branchesRef = new AtomicReference<List<HgTagBranch>>();
    new HgTagBranchCommand(project, root).listBranches(new Consumer<List<HgTagBranch>>() {
      @Override
      public void consume(final List<HgTagBranch> branches) {
        branchesRef.set(branches);
      }
    });
    return branchesRef.get();
  }

  private static void pushAll(final Project project, List<HgPushCommand> commands) {
    for (HgPushCommand command : commands) {
      final VirtualFile repo = command.getRepo();
      command.execute(new HgCommandResultHandler(  ) {

        @Override
        public void process(@Nullable HgCommandResult result) {
          int commitsNum = getNumberOfPushedCommits(result);
          String title = null;
          String description = null;
          if (commitsNum > 0) {
            title = "Pushed successfully";
            description = "Pushed " + commitsNum + " " + StringUtil.pluralize("commit", commitsNum) + " ["+ repo.getPresentableName() +"]";
          }
          else if (commitsNum == 0) {
            title = "";
            description = "Nothing to push [" + repo.getPresentableName() + "]";
          }
          new HgCommandResultNotifier(project).process(result, title, description);
        }
      });
    }

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

  private static List<HgPushCommand> preparePushCommands(Project project, HgPushDialog dialog) {
    List<HgPushDialog.HgRepositorySettings> repositorySettings = dialog.getRepositorySettings();

    List<HgPushCommand> result = new ArrayList<HgPushCommand>( repositorySettings.size() );

    for (HgPushDialog.HgRepositorySettings settings : repositorySettings) {
      if (settings.isSelected()) {
        HgPushCommand command = new HgPushCommand(project, settings.getRepository(), settings.getTarget());
        command.setRevision(settings.isRevisionSelected() ? settings.getRevision() : null);
        command.setForce(settings.isForce());
        command.setBranch(settings.isBranchSelected() ?settings.getBranch() : null);
        result.add(command);
      }
    }

    return result;
  }
}
