// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.checkout;

import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.dvcs.ui.CloneDvcsDialog;
import com.intellij.openapi.project.Project;
import com.intellij.vcs.git.GitDisplayName;
import git4idea.GitUtil;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.remote.GitRememberedInputs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @deprecated deprecated in favour of {@link com.intellij.util.ui.cloneDialog.VcsCloneDialog}
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated(forRemoval = true)
public class GitCloneDialog extends CloneDvcsDialog {
  private final @NotNull Git myGit;

  public GitCloneDialog(@NotNull Project project) {
    this(project, null);
  }

  public GitCloneDialog(@NotNull Project project, @Nullable String defaultUrl) {
    super(project, GitDisplayName.NAME, GitUtil.DOT_GIT, defaultUrl);
    myGit = Git.getInstance();
  }

  @Override
  protected @NotNull TestResult test(@NotNull String url) {
    GitCommandResult result = myGit.lsRemote(myProject, new File("."), url);
    return result.success() ? TestResult.SUCCESS : new TestResult(result.getErrorOutputAsJoinedString());
  }

  @Override
  protected @NotNull DvcsRememberedInputs getRememberedInputs() {
    return GitRememberedInputs.getInstance();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "GitCloneDialog";
  }
}