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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import git4idea.DialogManager;
import git4idea.Notificator;
import git4idea.config.GitVcsSettings;
import git4idea.test.GitExecutor;
import git4idea.test.GitTestUtil;
import git4idea.test.TestDialogManager;
import git4idea.test.TestNotificator;
import org.jetbrains.annotations.NotNull;
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

  @NotNull protected static final String HOST = "github.com";

  @NotNull protected Project myProject;
  @NotNull protected VirtualFile myProjectRoot;
  @NotNull protected GitVcsSettings myGitSettings;
  @NotNull protected GithubSettings myGitHubSettings;

  @NotNull protected TestDialogManager myDialogManager;
  @NotNull protected TestNotificator myNotificator;

  @NotNull private IdeaProjectTestFixture myProjectFixture;

  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors", "UnusedDeclaration"})
  protected GithubTest() {
    PlatformTestCase.initPlatformLangPrefix();
    GitTestUtil.setDefaultBuiltInServerPort();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getTestName(true)).getFixture();
    myProjectFixture.setUp();

    myProject = myProjectFixture.getProject();
    myProjectRoot = myProject.getBaseDir();

    String login = System.getProperty("test.github.login");
    String password = System.getProperty("test.github.password");

    // TODO change to assert when a stable Github testing server is ready
    assumeNotNull(login);
    assumeNotNull(password);

    myGitSettings = GitVcsSettings.getInstance(myProject);
    myGitSettings.getAppSettings().setPathToGit(GitExecutor.GIT_EXECUTABLE);

    myGitHubSettings = GithubSettings.getInstance();
    myGitHubSettings.setHost(HOST);
    myGitHubSettings.setLogin(login);
    myGitHubSettings.setPassword(password);

    myDialogManager = (TestDialogManager)ServiceManager.getService(DialogManager.class);
    myNotificator = (TestNotificator)ServiceManager.getService(myProject, Notificator.class);
  }

  @Override
  protected void tearDown() throws Exception {
    myProjectFixture.tearDown();
    super.tearDown();
  }

}
