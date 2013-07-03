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
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import git4idea.DialogManager;
import git4idea.Notificator;
import git4idea.commands.GitHttpAuthService;
import git4idea.commands.GitHttpAuthenticator;
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

import java.io.IOException;

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

  @NotNull protected TestDialogManager myDialogManager;
  @NotNull protected TestNotificator myNotificator;

  @NotNull private IdeaProjectTestFixture myProjectFixture;

  @NotNull protected GithubAuthData auth1;
  @NotNull protected GithubAuthData auth2;

  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors", "UnusedDeclaration"})
  protected GithubTest() {
    PlatformTestCase.initPlatformLangPrefix();
    GitTestUtil.setDefaultBuiltInServerPort();
  }

  protected void createProjectFiles() {
    createFile("file.txt");
    createFile("file");
    createFile("folder/file1");
    createFile("folder/file2");
    createFile("folder/empty_file", "");
    createFile("folder/empty_folder/");
    createFile("folder/dir/file3");
  }

  protected void createFile(@NotNull String path) {
    createFile(path, path.substring(path.lastIndexOf('/') + 1) + " content");
  }

  protected void createFile(@NotNull String path, @NotNull String content) {
    String[] pathElements = path.split("/");
    boolean lastIsDir = path.endsWith("/");
    VirtualFile currentParent = myProjectRoot;
    for (int i = 0; i < pathElements.length - 1; i++) {
      currentParent = createDir(myProject, currentParent, pathElements[i]);
    }

    String lastElement = pathElements[pathElements.length - 1];
    if (lastIsDir) {
      createDir(myProject, currentParent, lastElement);
    }
    else {
      createFile(myProject, currentParent, lastElement, content);
    }
  }

  protected static VirtualFile createFile(@NotNull Project project,
                                          @NotNull final VirtualFile parent,
                                          @NotNull final String name,
                                          @Nullable final String content) {
    final Ref<VirtualFile> result = new Ref<VirtualFile>();
    new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        try {
          VirtualFile file = parent.createChildData(this, name);
          if (content != null) {
            file.setBinaryContent(CharsetToolkit.getUtf8Bytes(content));
          }
          result.set(file);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }.execute();
    return result.get();
  }

  protected static VirtualFile createDir(@NotNull Project project, @NotNull final VirtualFile parent, @NotNull final String name) {
    final Ref<VirtualFile> result = new Ref<VirtualFile>();
    new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Throwable {
        try {
          VirtualFile dir = parent.findChild(name);
          if (dir == null) {
            dir = parent.createChildDirectory(this, name);
          }
          result.set(dir);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }.execute();
    return result.get();
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
        return myGitHubSettings.getPassword();
      }

      @NotNull
      @Override
      public String askUsername(@NotNull String url) {
        return myGitHubSettings.getLogin();
      }

      @Override
      public void saveAuthData() {
      }

      @Override
      public void forgetPassword() {
      }
    });
  }

  @Override
  protected void setUp() throws Exception {
    final String host = System.getenv("idea.test.github.host");
    final String login = System.getenv("idea.test.github.login1");
    final String password = System.getenv("idea.test.github.password1");
    final String login2 = System.getenv("idea.test.github.login2");
    final String password2 = System.getenv("idea.test.github.password2");

    // TODO change to assert when a stable Github testing server is ready
    assumeNotNull(host);
    assumeNotNull(login);
    assumeNotNull(password);
    assumeNotNull(login2);
    assumeNotNull(password2);

    super.setUp();

    myProjectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getTestName(true)).getFixture();
    myProjectFixture.setUp();

    myProject = myProjectFixture.getProject();
    myProjectRoot = myProject.getBaseDir();

    myGitSettings = GitVcsSettings.getInstance(myProject);
    myGitSettings.getAppSettings().setPathToGit(GitExecutor.GIT_EXECUTABLE);

    myGitHubSettings = GithubSettings.getInstance();
    myGitHubSettings.setHost(host);
    myGitHubSettings.setLogin(login);
    myGitHubSettings.setPassword(password);

    auth1 = new GithubAuthData(host, login, password);
    auth2 = new GithubAuthData(host, login2, password2);

    myDialogManager = (TestDialogManager)ServiceManager.getService(DialogManager.class);
    myNotificator = (TestNotificator)ServiceManager.getService(myProject, Notificator.class);
  }

  @Override
  protected void tearDown() throws Exception {
    myProjectFixture.tearDown();
    super.tearDown();
  }

}
