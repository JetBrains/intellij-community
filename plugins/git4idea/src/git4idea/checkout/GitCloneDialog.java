// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.checkout;

import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.dvcs.hosting.RepositoryHostingService;
import com.intellij.dvcs.ui.CloneDvcsDialog;
import com.intellij.openapi.project.Project;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.remote.GitRememberedInputs;
import git4idea.remote.GitRepositoryHostingService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

public class GitCloneDialog extends CloneDvcsDialog {

  @NotNull private final Git myGit;

  public GitCloneDialog(@NotNull Project project) {
    this(project, null);
  }

  public GitCloneDialog(@NotNull Project project, @Nullable String defaultUrl) {
    super(project, GitVcs.DISPLAY_NAME.get(), GitUtil.DOT_GIT, defaultUrl);
    myGit = Git.getInstance();
  }

  @Override
  @NotNull
  protected TestResult test(@NotNull String url) {
    GitCommandResult result = myGit.lsRemote(myProject, new File("."), url);
    return result.success() ? TestResult.SUCCESS : new TestResult(result.getErrorOutputAsJoinedString());
  }

  @NotNull
  @Override
  protected Collection<RepositoryHostingService> getRepositoryHostingServices() {
    return Arrays.asList(GitRepositoryHostingService.EP_NAME.getExtensions());
  }

  @NotNull
  @Override
  protected DvcsRememberedInputs getRememberedInputs() {
    return GitRememberedInputs.getInstance();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "GitCloneDialog";
  }
}