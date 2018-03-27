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
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubSettings;
import org.jetbrains.plugins.github.util.GithubUtil;

import static org.junit.Assume.assumeNotNull;

/**
 * <p>The base class for JUnit platform tests of the github plugin.<br/>
 *    Extend this test to write a test on GitHub which has the following features/limitations:
 * <ul>
 * <li>This is a "platform test case", which means that IDEA [almost] production platform is set up before the test starts.</li>
 * <li>Project base directory is the root of everything. </li>
 * </ul></p>
 * <p>All tests inherited from this class are required to have a login and a password to access the Github server.
 *    They are set up in Environment variables: <br/>
 *    <code>idea.test.github.host=myHost<br/>
 *          idea.test.github.login1=mylogin1<br/> // test user
 *          idea.test.github.login2=mylogin2<br/> // user with configured test repositories
 *          idea.test.github.password=mypassword</code> // password for test user
 * </p>
 *
 */
public abstract class GithubTest extends GitPlatformTest {

  @Nullable protected GitRepository myRepository;

  @NotNull protected GithubSettings myGitHubSettings;
  @NotNull private GitHttpAuthTestService myHttpAuthService;

  @NotNull protected GithubAuthData myAuth;
  @NotNull protected String myHost;
  @NotNull protected String myLogin1;
  @NotNull protected String myLogin2;
  @NotNull protected String myPassword;

  protected void createProjectFiles() {
    VfsTestUtil.createFile(projectRoot, "file.txt", "file.txt content");
    VfsTestUtil.createFile(projectRoot, "file", "file content");
    VfsTestUtil.createFile(projectRoot, "folder/file1", "file1 content");
    VfsTestUtil.createFile(projectRoot, "folder/file2", "file2 content");
    VfsTestUtil.createFile(projectRoot, "folder/empty_file");
    VfsTestUtil.createFile(projectRoot, "folder/dir/file3", "file3 content");
    VfsTestUtil.createDir (projectRoot, "folder/empty_folder");
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
        return myPassword;
      }

      @NotNull
      @Override
      public String askUsername(@NotNull String url) {
        return myLogin1;
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

    assertNotNull("GitHub remote is not configured", GithubUtil.findGithubRemoteUrl(myRepository));
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
    final String login1 = System.getenv("idea.test.github.login1");
    final String login2 = System.getenv("idea.test.github.login2");
    final String password = System.getenv("idea.test.github.password1");

    // TODO change to assert when a stable Github testing server is ready
    assumeNotNull(host);
    assumeNotNull(login1);
    assumeNotNull(password);

    myHost = host;
    myLogin1 = login1;
    myLogin2 = login2;
    myPassword = password;
    myAuth = GithubAuthData.createBasicAuth(host, login1, password);

    myGitHubSettings = GithubSettings.getInstance();
    myGitHubSettings.setAuthData(myAuth, true);

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
