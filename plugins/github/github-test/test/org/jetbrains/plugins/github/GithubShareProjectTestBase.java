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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.ui.DialogWrapper;
import git4idea.GitUtil;
import git4idea.Notificator;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.test.GitExecutor;
import git4idea.test.TestDialogHandler;
import git4idea.test.TestNotificator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.test.GithubTest;
import org.jetbrains.plugins.github.ui.GithubShareDialog;

import java.io.IOException;
import java.util.Random;

/**
 * @author Aleksey Pivovarov
 */
public abstract class GithubShareProjectTestBase extends GithubTest {
  protected static String PROJECT_NAME;

  @Override
  public void setUp() throws Exception {
    super.setUp();

    Random rnd = new Random();
    PROJECT_NAME = "new_project_from_share_test_" + rnd.nextLong();
    registerHttpAuthService();
  }

  @Override
  public void tearDown() throws Exception {
    deleteGithubRepo();
    super.tearDown();
  }

  protected void deleteGithubRepo() {
    try {
      GithubUtil.deleteGithubRepository(GithubUtil.getAuthData(), PROJECT_NAME);
    }
    catch (IOException e) {
      System.err.println(e.getMessage());
    }
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

  protected void checkGithubExists() {
    RepositoryInfo githubInfo = GithubUtil.getDetailedRepoInfo(GithubUtil.getAuthData(), GithubUtil.getAuthData().getLogin(), PROJECT_NAME);
    assertNotNull("Github repository does not exist", githubInfo);
  }

  protected void checkGitExists() {
    final GitRepositoryManager manager = GitUtil.getRepositoryManager(myProject);
    final GitRepository gitRepository = manager.getRepositoryForFile(myProjectRoot);
    assertNotNull("Git repository does not exist", gitRepository);
  }

  protected void checkRemoteConfigured() {
    final GitRepositoryManager manager = GitUtil.getRepositoryManager(myProject);
    final GitRepository gitRepository = manager.getRepositoryForFile(myProjectRoot);
    assertNotNull(gitRepository);

    assertNotNull("Github remote does not configured", GithubUtil.findGithubRemoteUrl(gitRepository));
  }

  protected void checkLastCommitPushed() {
    final GitRepositoryManager manager = GitUtil.getRepositoryManager(myProject);
    final GitRepository gitRepository = manager.getRepositoryForFile(myProjectRoot);
    assertNotNull(gitRepository);

    String hash = GitExecutor.git(gitRepository, "log -1 --pretty=%h");
    String ans = GitExecutor.git(gitRepository, "branch --contains " + hash + " -a");
    assertTrue(ans.contains("remotes/origin/master"));
  }
}
