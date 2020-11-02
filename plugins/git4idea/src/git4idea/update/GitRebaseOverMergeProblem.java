// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.update;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import git4idea.DialogManager;
import git4idea.GitCommit;
import git4idea.history.GitHistoryUtils;
import git4idea.i18n.GitBundle;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.List;
import java.util.Objects;

public final class GitRebaseOverMergeProblem {
  private static final Logger LOG = Logger.getInstance(GitRebaseOverMergeProblem.class);

  public enum Decision {
    MERGE_INSTEAD("rebasing.merge.commits.button.merge"),
    REBASE_ANYWAY("rebasing.merge.commits.button.rebase"),
    CANCEL_OPERATION("rebasing.merge.commits.button.cancel");

    private final String myButtonTextKey;

    Decision(@NotNull @PropertyKey(resourceBundle = GitBundle.BUNDLE) String buttonTextKey) {
      myButtonTextKey = buttonTextKey;
    }

    private static @NlsContexts.Button String @NotNull [] getButtonTitles() {
      return ContainerUtil.map2Array(values(), String.class, decision -> GitBundle.message(decision.myButtonTextKey));
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
    int decision =
      DialogManager.showMessage(GitBundle.message("dialog.message.rebasing.merge.commits"),
                                GitBundle.message("dialog.title.rebasing.merge.commits"), Decision.getButtonTitles(),
                                Decision.getDefaultButtonIndex(),
                                Decision.getFocusedButtonIndex(), Messages.getWarningIcon(), null);
    return Decision.getOption(decision);
  }
}
