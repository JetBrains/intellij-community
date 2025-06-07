// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.push;

import com.intellij.dvcs.push.PushSpec;
import com.intellij.dvcs.push.Pusher;
import com.intellij.dvcs.push.VcsPushOptionValue;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.command.HgPushCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.repo.HgRepository;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.zmlx.hg4idea.HgNotificationIdsHolder.*;

public final class HgPusher extends Pusher<HgRepository, HgPushSource, HgTarget> {
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

  public static void pushSynchronously(final @NotNull Project project, @NotNull HgPushCommand command) {
    final VirtualFile repo = command.getRepo();
    HgCommandResult result = command.executeInCurrentThread();
    if (result == null) {
      return;
    }

    if (result.getExitValue() == PUSH_SUCCEEDED_EXIT_VALUE) {
      int commitsNum = getNumberOfPushedCommits(result);
      String successTitle = HgBundle.message("action.hg4idea.push.success");
      String successDescription = HgBundle.message("action.hg4idea.push.success.msg",
                                                   commitsNum,
                                                   repo.getPresentableName());
      VcsNotifier.getInstance(project).notifySuccess(PUSH_SUCCESS, successTitle, successDescription);
    }
    else if (result.getExitValue() == NOTHING_TO_PUSH_EXIT_VALUE) {
      VcsNotifier.getInstance(project).notifySuccess(NOTHING_TO_PUSH, "", HgBundle.message("action.hg4idea.push.nothing"));
    }
    else {
      new HgCommandResultNotifier(project).notifyError(PUSH_ERROR,
                                                       result,
                                                       HgBundle.message("action.hg4idea.push.error"),
                                                       HgBundle.message("action.hg4idea.push.error.msg", repo.getPresentableName()));
    }
  }

  @VisibleForTesting
  public static int getNumberOfPushedCommits(@NotNull HgCommandResult result) {
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
