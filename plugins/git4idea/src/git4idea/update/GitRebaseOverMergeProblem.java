// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.DialogManager;
import git4idea.GitCommit;
import git4idea.history.GitHistoryUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public class GitRebaseOverMergeProblem {
  private static final Logger LOG = Logger.getInstance(GitRebaseOverMergeProblem.class);
  public static final String DESCRIPTION =
    "You are about to rebase a merge commit with conflicts.\n\n" +
    "Choose 'Merge' if you don't want to resolve conflicts again, " +
    "or you still can rebase if you want to linearize the history.";

  public enum Decision {
    MERGE_INSTEAD("Merge"),
    REBASE_ANYWAY("Rebase"),
    CANCEL_OPERATION(CommonBundle.getCancelButtonText());

    private final String myButtonText;

    Decision(@NotNull String buttonText) {
      myButtonText = buttonText;
    }

    private static String @NotNull [] getButtonTitles() {
      return ContainerUtil.map2Array(values(), String.class, decision -> decision.myButtonText);
    }

    @NotNull
    public static Decision getOption(final int index) {
      return Objects.requireNonNull(ContainerUtil.find(values(), decision -> decision.ordinal() == index));
    }

    private static int getDefaultButtonIndex() {
      return MERGE_INSTEAD.ordinal();
    }

    private static int getFocusedButtonIndex() {
      return REBASE_ANYWAY.ordinal();
    }
  }

  public static boolean hasProblem(@NotNull Project project,
                                   @NotNull VirtualFile root,
                                   @NotNull String baseRef,
                                   @NotNull String currentRef) {
    String range = baseRef + ".." + currentRef;
    try {
      List<GitCommit> commits = GitHistoryUtils.history(project, root, range, "--merges");
      return StreamEx.of(commits).anyMatch(commit -> !commit.getChanges().isEmpty());
    }
    catch (VcsException e) {
      LOG.warn("Couldn't get git log --merges " + range, e);
      return false;
    }
  }

  @NotNull
  public static Decision showDialog() {
    final Ref<Decision> decision = Ref.create();
    ApplicationManager.getApplication().invokeAndWait(() -> decision.set(doShowDialog()));
    return decision.get();
  }

  @NotNull
  private static Decision doShowDialog() {
    int decision = DialogManager.showMessage(DESCRIPTION, "Rebasing Merge Commits", Decision.getButtonTitles(),
                                             Decision.getDefaultButtonIndex(),
                                             Decision.getFocusedButtonIndex(), Messages.getWarningIcon(), null);
    return Decision.getOption(decision);
  }
}
