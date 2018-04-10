/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Clock;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.text.DateFormatUtil;
import git4idea.actions.GitInit;
import git4idea.commands.Git;
import git4idea.repo.GitRepository;
import git4idea.test.GitExecutor;
import git4idea.test.TestDialogHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.GithubFullPath;
import org.jetbrains.plugins.github.test.GithubTest;
import org.jetbrains.plugins.github.ui.GithubCreatePullRequestDialog;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jetbrains.plugins.github.util.GithubUtil;

import java.util.Random;

import static com.intellij.openapi.vcs.Executor.cd;

/**
 * @author Aleksey Pivovarov
 */
public abstract class GithubCreatePullRequestTestBase extends GithubTest {
  protected static final String PROJECT_URL = "https://github.com/ideatest1/PullRequestTest";
  protected static final String PROJECT_NAME = "PullRequestTest";
  protected String BRANCH_NAME;

  @Override
  protected void beforeTest() {
    Random rnd = new Random();
    long time = Clock.getTime();
    BRANCH_NAME = "branch_" + getTestName(false) + "_" + DateFormatUtil.formatDate(time).replace('/', '-') + "_" + rnd.nextLong();

    registerHttpAuthService();

    cd(projectRoot.getPath());
    cloneRepo();
    createBranch();
    createChanges();
    initGitChecks();
  }

  @Override
  protected void afterTest() {
    deleteRemoteBranch();
  }

  protected void deleteRemoteBranch() {
    GitRepository repository = GithubUtil.getGitRepository(myProject, projectRoot);
    if (repository != null) {
      Git.getInstance().push(repository, "origin", PROJECT_URL, ":" + BRANCH_NAME, false);
    }
  }

  protected void registerDefaultCreatePullRequestDialogHandler(@NotNull final String branch, @NotNull final String user) {
    dialogManager.registerDialogHandler(GithubCreatePullRequestDialog.class, new TestDialogHandler<GithubCreatePullRequestDialog>() {
      @Override
      public int handleDialog(GithubCreatePullRequestDialog dialog) {
        dialog.testSetRequestTitle(BRANCH_NAME);
        dialog.testSetFork(new GithubFullPath(user, PROJECT_NAME));
        dialog.testSetBranch(branch);
        dialog.testCreatePullRequest();
        return DialogWrapper.OK_EXIT_CODE;
      }
    });
  }

  protected void cloneRepo() {
    // project dir can be not empty
    git("init");
    git("remote add origin " + PROJECT_URL);
    git("fetch");
    git("checkout -t origin/master");

    setGitIdentity(projectRoot);
    GitInit.refreshAndConfigureVcsMappings(myProject, projectRoot, projectRoot.getPath());
  }

  protected void addRemote(@NotNull String user) {
    git("remote add somename " + GithubUrlUtil.getCloneUrl(new GithubFullPath(user, PROJECT_NAME)));
  }

  protected void createBranch() {
    git("branch " + BRANCH_NAME);
    git("checkout " + BRANCH_NAME);
  }

  protected void createChanges() {
    VfsTestUtil.createFile(projectRoot, "file.txt", "file.txt content");
    git("add file.txt");
    git("commit -m changes");
  }
}
