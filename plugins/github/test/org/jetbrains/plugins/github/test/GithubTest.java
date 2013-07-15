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
package org.jetbrains.plugins.github.test;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import git4idea.DialogManager;
import git4idea.Notificator;
import git4idea.commands.GitHttpAuthService;
import git4idea.commands.GitHttpAuthenticator;
import git4idea.config.GitConfigUtil;
import git4idea.config.GitVcsSettings;
import git4idea.remote.GitHttpAuthTestService;
import git4idea.test.GitExecutor;
import git4idea.test.GitTestUtil;
import git4idea.test.TestDialogManager;
import git4idea.test.TestNotificator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.GithubAuthData;
import org.jetbrains.plugins.github.GithubSettings;

import static org.junit.Assume.assumeNotNull;

/**
 * <p>The base class for JUnit platform tests of the github plugin.<br/>
 *    Extend this test to write a test on GitHub which has the following features/limitations:
 * <ul>
 * <li>This is a "platform test case", which means that IDEA [almost] production platform is set up before the test starts.</li>
 * <li>Project base directory is the root of everything. </li>
 * </ul></p>
 * <p>All tests inherited from this class are required to have a login and a password to access the Github server.
 *    They are set up in System properties: <br/>
 *    <code>-Dtest.github.login=mylogin<br/>
 *           -Dtest.github.password=mypassword</code>
 * </p>
 *
 * @author Kirill Likhodedov
 */
public abstract class GithubTest extends UsefulTestCase {

  @NotNull protected Project myProject;
  @NotNull protected VirtualFile myProjectRoot;
  @NotNull protected GitVcsSettings myGitSettings;
  @NotNull protected GithubSettings myGitHubSettings;
  @NotNull private GitHttpAuthTestService myHttpAuthService;

  @NotNull protected TestDialogManager myDialogManager;
  @NotNull protected TestNotificator myNotificator;

  @NotNull private IdeaProjectTestFixture myProjectFixture;

  @NotNull protected GithubAuthData myAuth;
  @NotNull protected String myHost;
  @NotNull protected String myLogin1;
  @NotNull protected String myLogin2;
  @NotNull protected String myPassword;

  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors", "UnusedDeclaration"})
  protected GithubTest() {
    PlatformTestCase.initPlatformLangPrefix();
    GitTestUtil.setDefaultBuiltInServerPort();
  }

  protected void createProjectFiles() {
    VfsTestUtil.createFile(myProjectRoot, "file.txt", "file.txt content");
    VfsTestUtil.createFile(myProjectRoot, "file", "file content");
    VfsTestUtil.createFile(myProjectRoot, "folder/file1", "file1 content");
    VfsTestUtil.createFile(myProjectRoot, "folder/file2", "file2 content");
    VfsTestUtil.createFile(myProjectRoot, "folder/empty_file");
    VfsTestUtil.createFile(myProjectRoot, "folder/dir/file3", "file3 content");
    VfsTestUtil.createDir (myProjectRoot, "folder/empty_folder");
  }

  protected void checkNotification(@NotNull NotificationType type, @Nullable String title, @Nullable String content) {
    Notification actualNotification = myNotificator.getLastNotification();
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

  @Override
  protected void setUp() throws Exception {
    final String host = System.getenv("idea.test.github.host");
    final String login1 = System.getenv("idea.test.github.login1");
    final String login2 = System.getenv("idea.test.github.login2");
    final String password = System.getenv("idea.test.github.password1");

    // TODO change to assert when a stable Github testing server is ready
    assumeNotNull(host);
    assumeNotNull(login1);
    assumeNotNull(password);

    super.setUp();

    myProjectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getTestName(true)).getFixture();
    myProjectFixture.setUp();

    myProject = myProjectFixture.getProject();
    myProjectRoot = myProject.getBaseDir();

    myGitSettings = GitVcsSettings.getInstance(myProject);
    myGitSettings.getAppSettings().setPathToGit(GitExecutor.GIT_EXECUTABLE);

    myHost = host;
    myLogin1 = login1;
    myLogin2 = login2;
    myPassword = password;
    myAuth = GithubAuthData.createBasicAuth(host, login1, password);

    myGitHubSettings = GithubSettings.getInstance();
    myGitHubSettings.setCredentials(myHost, myLogin1, myAuth, false);

    myDialogManager = (TestDialogManager)ServiceManager.getService(DialogManager.class);
    myNotificator = (TestNotificator)ServiceManager.getService(myProject, Notificator.class);
    myHttpAuthService = (GitHttpAuthTestService)ServiceManager.getService(GitHttpAuthService.class);
  }

  @Override
  protected void tearDown() throws Exception {
    myHttpAuthService.cleanup();
    myDialogManager.cleanup();
    myNotificator.cleanup();

    myProjectFixture.tearDown();
    super.tearDown();
  }

}
