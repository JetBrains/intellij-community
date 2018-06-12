/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.test;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.VfsTestUtil;
import git4idea.commands.GitHttpAuthService;
import git4idea.commands.GitHttpAuthenticator;
import git4idea.config.GitConfigUtil;
import git4idea.repo.GitRepository;
import git4idea.test.GitExecutor;
import git4idea.test.GitHttpAuthTestService;
import git4idea.test.GitPlatformTest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.GithubApiTaskExecutor;
import org.jetbrains.plugins.github.api.GithubApiUtil;
import org.jetbrains.plugins.github.authentication.GithubAuthenticationManager;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.util.GithubGitHelper;
import org.jetbrains.plugins.github.util.GithubSettings;
import org.jetbrains.plugins.github.util.GithubUtil;

import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

/**
 * <p>The base class for JUnit platform tests of the github plugin.<br/>
 * Extend this test to write a test on GitHub which has the following features/limitations:
 * <ul>
 * <li>This is a "platform test case", which means that IDEA [almost] production platform is set up before the test starts.</li>
 * <li>Project base directory is the root of everything. </li>
 * </ul></p>
 * <p>All tests inherited from this class are required to have a token to access the Github server.
 * They are set up in Environment variables: <br/>
 * <code>idea.test.github.host<br/>
 * idea.test.github.token1<br/> // token test user
 * idea.test.github.token2</code> // token user with configured test repositories
 * </p>
 */
public abstract class GithubTest extends GitPlatformTest {

  @Nullable protected GitRepository myRepository;

  @NotNull private GitHttpAuthTestService myHttpAuthService;
  @NotNull protected GithubAuthenticationManager myAuthenticationManager;
  @NotNull protected GithubApiTaskExecutor myApiTaskExecutor;

  @NotNull protected GithubAccount myAccount;
  @NotNull protected GithubAccount myAccount2;

  @NotNull protected String myUsername;
  @NotNull protected String myUsername2;

  @Nullable private String myToken;

  protected void createProjectFiles() {
    VfsTestUtil.createFile(projectRoot, "file.txt", "file.txt content");
    VfsTestUtil.createFile(projectRoot, "file", "file content");
    VfsTestUtil.createFile(projectRoot, "folder/file1", "file1 content");
    VfsTestUtil.createFile(projectRoot, "folder/file2", "file2 content");
    VfsTestUtil.createFile(projectRoot, "folder/empty_file");
    VfsTestUtil.createFile(projectRoot, "folder/dir/file3", "file3 content");
    VfsTestUtil.createDir(projectRoot, "folder/empty_folder");
  }

  @Override
  protected boolean hasRemoteGitOperation() {
    return true;
  }

  protected void checkNotification(@NotNull NotificationType type, @Nullable String title, @Nullable String content) {
    Notification actualNotification = vcsNotifier.getLastNotification();
    assertNotNull("No notification was shown", actualNotification);

    if (title != null) {
      assertEquals("Notification has wrong title (content: " + actualNotification.getContent() + ")", title, actualNotification.getTitle());
    }
    if (content != null) {
      assertEquals("Notification has wrong content", content, actualNotification.getContent());
    }
    assertEquals("Notification has wrong type", type, actualNotification.getType());
  }

  protected void registerHttpAuthService() {
    GitHttpAuthTestService myHttpAuthService = (GitHttpAuthTestService)ServiceManager.getService(GitHttpAuthService.class);
    myHttpAuthService.register(new GitHttpAuthenticator() {
      @NotNull
      @Override
      public String askPassword(@NotNull String url) {
        return GithubUtil.GIT_AUTH_PASSWORD_SUBSTITUTE;
      }

      @NotNull
      @Override
      public String askUsername(@NotNull String url) {
        return myToken;
      }

      @Override
      public void saveAuthData() {
      }

      @Override
      public void forgetPassword() {
      }

      @Override
      public boolean wasCancelled() {
        return false;
      }
    });
  }

  // workaround: user on test server got "" as username, so git can't generate default identity
  protected void setGitIdentity(VirtualFile root) {
    try {
      GitConfigUtil.setValue(myProject, root, "user.name", "Github Test");
      GitConfigUtil.setValue(myProject, root, "user.email", "githubtest@jetbrains.com");
    }
    catch (VcsException e) {
      e.printStackTrace();
    }
  }

  protected void initGitChecks() {
    myRepository = repositoryManager.getRepositoryForFile(projectRoot);
  }

  protected void checkGitExists() {
    assertNotNull("Git repository does not exist", myRepository);
  }

  protected void checkRemoteConfigured() {
    assertNotNull(myRepository);

    assertTrue("GitHub remote is not configured", GithubGitHelper.getInstance().hasAccessibleRemotes(myRepository));
  }

  protected void checkLastCommitPushed() {
    assertNotNull(myRepository);

    String hash = GitExecutor.git(myRepository, "log -1 --pretty=%h");
    String ans = GitExecutor.git(myRepository, "branch --contains " + hash + " -a");
    assertTrue(ans.contains("remotes/origin"));
  }

  @Override
  protected final void setUp() throws Exception {
    super.setUp();

    final String host = System.getenv("idea.test.github.host");
    final String token1 = System.getenv("idea.test.github.token1");
    final String token2 = System.getenv("idea.test.github.token2");

    // TODO change to assert when a stable Github testing server is ready
    assumeNotNull(host);
    assumeTrue(token1 != null && token2 != null);
    myAuthenticationManager = GithubAuthenticationManager.getInstance();
    myApiTaskExecutor = GithubApiTaskExecutor.getInstance();
    myAccount = myAuthenticationManager.registerAccount("account1", host, token1);
    myAccount2 = myAuthenticationManager.registerAccount("account2", host, token2);
    myToken = token1;

    myUsername = myApiTaskExecutor.execute(myAccount, c -> GithubApiUtil.getCurrentUser(c).getLogin());
    myUsername2 = myApiTaskExecutor.execute(myAccount2, c -> GithubApiUtil.getCurrentUser(c).getLogin());

    myHttpAuthService = (GitHttpAuthTestService)ServiceManager.getService(GitHttpAuthService.class);

    try {
      beforeTest();
    }
    catch (Exception e) {
      try {
        tearDown();
      }
      catch (Exception e2) {
        e2.printStackTrace();
      }
      throw e;
    }
  }

  @Override
  protected final void tearDown() throws Exception {
    try {
      afterTest();
      myAuthenticationManager.setDefaultAccount(myProject, null);
      myAuthenticationManager.clearAccounts();
    }
    finally {
      if (myHttpAuthService != null) myHttpAuthService.cleanup();
      super.tearDown();
    }
  }

  protected void beforeTest() {
  }

  protected void afterTest() throws Exception {
  }

  @Override
  protected boolean runInDispatchThread() {
    return true;
  }


  protected void git(@NotNull String command) {
    GitExecutor.git(this, command, false);
  }
}
