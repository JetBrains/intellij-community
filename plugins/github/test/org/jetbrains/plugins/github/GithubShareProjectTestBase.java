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
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.test.GitExecutor;
import git4idea.test.TestDialogHandler;
import org.jetbrains.plugins.github.test.GithubTest;
import org.jetbrains.plugins.github.ui.GithubShareDialog;

import java.io.IOException;
import java.util.Random;

/**
 * @author Aleksey Pivovarov
 */
public abstract class GithubShareProjectTestBase extends GithubTest {
  protected String PROJECT_NAME;
  protected GitRepositoryManager myGitRepositoryManager;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    myGitRepositoryManager = GitUtil.getRepositoryManager(myProject);
    Random rnd = new Random();
    PROJECT_NAME = "new_project_from_share_test_" + rnd.nextLong();
    registerHttpAuthService();
  }

  @Override
  public void tearDown() throws Exception {
    try {
      deleteGithubRepo();
    }
    finally {
      super.tearDown();
    }
  }

  protected void deleteGithubRepo() throws IOException {
    GithubUtil.deleteGithubRepository(myGitHubSettings.getAuthData(), PROJECT_NAME);
  }

  protected void registerDefaultShareDialogHandler() {
    myDialogManager.registerDialogHandler(GithubShareDialog.class, new TestDialogHandler<GithubShareDialog>() {
      @Override
      public int handleDialog(GithubShareDialog dialog) {
        dialog.setRepositoryName(PROJECT_NAME);
        return DialogWrapper.OK_EXIT_CODE;
      }
    });
  }

  protected void registerDefaultUntrackedFilesDialogHandler() {
    myDialogManager.registerDialogHandler(GithubShareAction.GithubUntrackedFilesDialog.class,
                                          new TestDialogHandler<GithubShareAction.GithubUntrackedFilesDialog>() {
                                            @Override
                                            public int handleDialog(GithubShareAction.GithubUntrackedFilesDialog dialog) {
                                              return DialogWrapper.OK_EXIT_CODE;
                                            }
                                          });
  }

  protected void checkGithubExists() throws IOException {
    GithubAuthData auth = myGitHubSettings.getAuthData();
    RepositoryInfo githubInfo = GithubUtil.getDetailedRepoInfo(auth, auth.getLogin(), PROJECT_NAME);
    assertNotNull("GitHub repository does not exist", githubInfo);
  }

  protected void checkGitExists() {
    final GitRepository gitRepository = myGitRepositoryManager.getRepositoryForFile(myProjectRoot);
    assertNotNull("Git repository does not exist", gitRepository);
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
    assertTrue(ans.contains("remotes/origin/master"));
  }
}
