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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Clock;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.text.DateFormatUtil;
import git4idea.GitUtil;
import git4idea.actions.GitInit;
import git4idea.commands.Git;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.test.GitExecutor;
import git4idea.test.TestDialogHandler;
import org.jetbrains.plugins.github.test.GithubTest;
import org.jetbrains.plugins.github.ui.GithubCreatePullRequestDialog;

import java.util.Random;

import static git4idea.test.GitExecutor.git;

/**
 * @author Aleksey Pivovarov
 */
public abstract class GithubCreatePullRequestTestBase extends GithubTest {
  protected static final String PROJECT_URL = "https://github.com/ideatest1/PullRequestTest";
  protected String BRANCH_NAME;
  protected GitRepositoryManager myGitRepositoryManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myGitRepositoryManager = GitUtil.getRepositoryManager(myProject);
    Random rnd = new Random();
    long time = Clock.getTime();
    BRANCH_NAME = "branch_" + getTestName(false) + "_" + DateFormatUtil.formatDate(time).replace('/', '-') + "_" + rnd.nextLong();

    registerHttpAuthService();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      deleteRemoteBranch();
    }
    finally {
      super.tearDown();
    }
  }

  protected void deleteRemoteBranch() {
    GitRepository repository = GithubUtil.getGitRepository(myProject, myProjectRoot);
    if (repository != null) {
      ServiceManager.getService(Git.class).push(repository, "origin", PROJECT_URL, ":" + BRANCH_NAME);
    }
  }

  protected void registerDefaultCreatePullRequestDialogHandler(final String branch) {
    myDialogManager.registerDialogHandler(GithubCreatePullRequestDialog.class, new TestDialogHandler<GithubCreatePullRequestDialog>() {
      @Override
      public int handleDialog(GithubCreatePullRequestDialog dialog) {
        dialog.setRequestTitle(BRANCH_NAME);
        dialog.setBranch(branch);
        return DialogWrapper.OK_EXIT_CODE;
      }
    });
  }

  protected void cloneRepo() {
    git("clone " + PROJECT_URL + " .");
    setGitIdentity(myProjectRoot);
    GitInit.refreshAndConfigureVcsMappings(myProject, myProjectRoot, myProjectRoot.getPath());
  }

  protected void createBranch() {
    git("branch " + BRANCH_NAME);
    git("checkout " + BRANCH_NAME);
    GitInit.refreshAndConfigureVcsMappings(myProject, myProjectRoot, myProjectRoot.getPath());
  }

  protected void createChanges() {
    VfsTestUtil.createFile(myProjectRoot, "file.txt", "file.txt content");
    git("add file.txt");
    git("commit -m changes");
  }

  protected void checkRemoteConfigured() {
    final GitRepository gitRepository = myGitRepositoryManager.getRepositoryForFile(myProjectRoot);
    assertNotNull(gitRepository);

    assertNotNull("GitHub remote is not configured", GithubUtil.findGithubRemoteUrl(gitRepository));
  }

  protected void checkLastCommitPushed() {
    final GitRepository gitRepository = myGitRepositoryManager.getRepositoryForFile(myProjectRoot);
    assertNotNull(gitRepository);

    String hash = GitExecutor.git(gitRepository, "log -1 --pretty=%h");
    String ans = GitExecutor.git(gitRepository, "branch --contains " + hash + " -a");
    assertTrue(ans.contains("remotes/origin/"));
  }
}
