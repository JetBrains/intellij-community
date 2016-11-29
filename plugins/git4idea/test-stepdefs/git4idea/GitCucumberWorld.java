/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package git4idea;

import com.intellij.idea.IdeaTestApplication;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.UIUtil;
import cucumber.api.Scenario;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import git4idea.commands.Git;
import git4idea.commands.GitHttpAuthService;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.test.GitExecutor;
import git4idea.test.GitHttpAuthTestService;
import git4idea.test.GitTestUtil;
import git4idea.test.MockVcsHelper;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.vcs.Executor.cd;
import static com.intellij.openapi.vcs.Executor.mkdir;

/**
 * <p>The container of test environment variables which should be visible from any step definition script.</p>
 * <p>Most of the fields are populated in the Before hook of the {@link GeneralStepdefs}.</p>
 */
public class GitCucumberWorld {

  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  public static String myTestRoot;
  public static String myProjectRoot;
  public static VirtualFile myProjectDir;
  public static Project myProject;

  public static Git myGit;
  public static GitRepository myRepository;
  public static GitVcsSettings mySettings;
  public static ChangeListManagerImpl myChangeListManager;
  public static GitRepositoryManager myRepositoryManager;
  public static GitVcs myVcs;

  public static MockVcsHelper myVcsHelper;
  public static TestVcsNotifier myNotificator;

  public static GitHttpAuthTestService myHttpAuthService; // only with @remote tag

  public static GitTestVirtualCommitsHolder virtualCommits;

  private static Collection<Future> myAsyncTasks;

  private final Logger LOG = Logger.getInstance(GitCucumberWorld.class);
  private IdeaProjectTestFixture myProjectFixture;
  private String myTestName;

  @Before
  public void setUp() throws Throwable {
    IdeaTestApplication.getInstance();

    myTestName = createTestName();
    myProjectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(myTestName).getFixture();

    edt(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        myProjectFixture.setUp();
      }
    });

    myProject = myProjectFixture.getProject();

    myProjectRoot = myProject.getBasePath();
    myProjectDir = myProject.getBaseDir();
    myTestRoot = myProjectRoot;

    myGit = ServiceManager.getService(myProject, Git.class);
    mySettings = GitVcsSettings.getInstance(myProject);
    mySettings.getAppSettings().setPathToGit(GitExecutor.PathHolder.GIT_EXECUTABLE);

    // dynamic overriding is used instead of making it in plugin.xml,
    // because MockVcsHelper is not ready to be a full featured implementation for all tests.
    myVcsHelper = GitTestUtil.overrideService(myProject, AbstractVcsHelper.class, MockVcsHelper.class);
    myChangeListManager = ChangeListManagerImpl.getInstanceImpl(myProject);
    myNotificator = (TestVcsNotifier)ServiceManager.getService(myProject, VcsNotifier.class);
    myVcs = GitVcs.getInstance(myProject);
    myRepositoryManager = GitUtil.getRepositoryManager(myProject);

    virtualCommits = new GitTestVirtualCommitsHolder();
    myAsyncTasks = new ArrayList<>();

    cd(myProjectRoot);
    myRepository = GitTestUtil.createRepository(myProject, myProjectRoot);

    ProjectLevelVcsManagerImpl vcsManager = (ProjectLevelVcsManagerImpl)ProjectLevelVcsManager.getInstance(myProject);
    AbstractVcs vcs = vcsManager.findVcsByName("Git");
    Assert.assertEquals(1, vcsManager.getRootsUnderVcs(vcs).length);

    GitTestUtil.assumeSupportedGitVersion(myVcs);
    LOG.info(getStartTestMarker());
  }

  private String getStartTestMarker() {
    return "Starting " + myTestName;
  }

  // TODO should take actual feature name once we migrate to more recent cucumber lib
  private String createTestName() {
    return getClass().getName() + "-" + new Random().nextInt();
  }

  @Before(value = "@remote", order = 1)
  public void setUpRemoteOperations() {
    myHttpAuthService = (GitHttpAuthTestService)ServiceManager.getService(GitHttpAuthService.class);
  }

  @Before(value = "@nestedroot", order = 2)
  public void setUpStandardMultipleRootsConfig() {
    cd(myProjectRoot);
    File community = mkdir("community");
    GitTestUtil.createRepository(myProject, community.getPath());
  }

  @After(value = "@remote", order = 5)
  public void tearDownRemoteOperations() {
  }

  @After(order = 4)
  public void waitForPendingTasks() throws InterruptedException, ExecutionException, TimeoutException {
    for (Future future : myAsyncTasks) {
      future.get(30, TimeUnit.SECONDS);
    }
  }

  @After(order = 3)
  public void tearDownFixture() throws Exception {
    edt(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        myProjectFixture.tearDown();
      }
    });
  }

  @After(order = 2)
  public void cleanupDir() {
    FileUtil.delete(new File(myTestRoot));
  }

  @After(order = 1)
  public void cleanupWorld() throws IllegalAccessException {
    for (Field field : GitCucumberWorld.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        field.set(null, null);
      }
    }
  }

  @After(order = 0)
  public void dumpToLog(@NotNull Scenario result) {
    if (result.isFailed()) {
      TestLoggerFactory.dumpLogToStdout(getStartTestMarker());
    }
  }

  public static void executeOnPooledThread(Runnable runnable) {
    myAsyncTasks.add(ApplicationManager.getApplication().executeOnPooledThread(runnable));
  }

  private static void edt(@NotNull final ThrowableRunnable<Exception> runnable) throws Exception {
    final AtomicReference<Exception> exception = new AtomicReference<>();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          runnable.run();
        }
        catch (Exception throwable) {
          exception.set(throwable);
        }
      }
    });
    //noinspection ThrowableResultOfMethodCallIgnored
    if (exception.get() != null) {
      throw exception.get();
    }
  }

}
