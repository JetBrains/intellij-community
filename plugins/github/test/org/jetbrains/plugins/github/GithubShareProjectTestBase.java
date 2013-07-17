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
import com.intellij.util.text.DateFormatUtil;
import git4idea.GitUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.test.GitExecutor;
import git4idea.test.TestDialogHandler;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.api.GithubRepoDetailed;
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
    long time = Clock.getTime();
    PROJECT_NAME = "new_project_from_" + getTestName(false) + "_" + DateFormatUtil.formatDate(time).replace('/', '-') + "_" + rnd.nextLong();
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
    GithubApiUtil.deleteGithubRepository(myGitHubSettings.getAuthData(), myLogin1, PROJECT_NAME);
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
                                              // actually we should ask user for name/email ourselves (like in CommitDialog)
                                              for (GitRepository repository : GitUtil.getRepositoryManager(myProject).getRepositories()) {
                                                setGitIdentity(repository.getRoot());
                                              }
                                              return DialogWrapper.OK_EXIT_CODE;
                                            }
                                          });
  }

  protected void checkGithubExists() throws IOException {
    GithubAuthData auth = myGitHubSettings.getAuthData();
    GithubRepoDetailed githubInfo = GithubApiUtil.getDetailedRepoInfo(auth, myLogin1, PROJECT_NAME);
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
