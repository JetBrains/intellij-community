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
package git4idea.test;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.vcs.AbstractVcsTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import git4idea.DialogManager;
import git4idea.GitPlatformFacade;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitHandler;
import git4idea.config.GitVcsSettings;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public abstract class GitPlatformTest extends UsefulTestCase {

  static {
    Logger.setFactory(TestLoggerFactory.class);
  }

  private static final Logger LOG = Logger.getInstance(GitPlatformTest.class);

  protected Project myProject;
  protected VirtualFile myProjectRoot;
  protected String myProjectPath;
  protected GitRepositoryManager myGitRepositoryManager;
  protected GitVcsSettings myGitSettings;
  protected GitPlatformFacade myPlatformFacade;
  protected Git myGit;
  protected GitVcs myVcs;

  protected TestDialogManager myDialogManager;
  protected TestVcsNotifier myVcsNotifier;

  private IdeaProjectTestFixture myProjectFixture;
  private String myTestStartedIndicator;

  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors", "UnusedDeclaration"})
  protected GitPlatformTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableDebugLogging();

    try {
      myProjectFixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(getTestName(true)).getFixture();
      myProjectFixture.setUp();
    }
    catch (Exception e) {
      super.tearDown();
      throw e;
    }

    try {
      myProject = myProjectFixture.getProject();
      myProjectRoot = myProject.getBaseDir();
      myProjectPath = myProjectRoot.getPath();

      myGitSettings = GitVcsSettings.getInstance(myProject);
      myGitSettings.getAppSettings().setPathToGit(GitExecutor.PathHolder.GIT_EXECUTABLE);

      myDialogManager = (TestDialogManager)ServiceManager.getService(DialogManager.class);
      myVcsNotifier = (TestVcsNotifier)ServiceManager.getService(myProject, VcsNotifier.class);

      myGitRepositoryManager = GitUtil.getRepositoryManager(myProject);
      myPlatformFacade = ServiceManager.getService(myProject, GitPlatformFacade.class);
      myGit = ServiceManager.getService(myProject, Git.class);
      myVcs = ObjectUtils.assertNotNull(GitVcs.getInstance(myProject));
      myVcs.doActivate();

      GitTestUtil.assumeSupportedGitVersion(myVcs);
      addSilently();
      removeSilently();
    }
    catch (Exception e) {
      tearDown();
      throw e;
    }
  }

  @Override
  @NotNull
  public String getTestName(boolean lowercaseFirstLetter) {
    String name = super.getTestName(lowercaseFirstLetter);
    name = StringUtil.shortenTextWithEllipsis(name.trim().replace(" ", "_"), 12, 6, "_");
    if (name.startsWith("_")) {
      name = name.substring(1);
    }
    return name;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      if (myDialogManager != null) {
        myDialogManager.cleanup();
      }
      if (myVcsNotifier != null) {
        myVcsNotifier.cleanup();
      }
      if (myProjectFixture != null) {
        myProjectFixture.tearDown();
      }
    }
    finally {
      try {
        String tempTestIndicator = myTestStartedIndicator;
        clearFields(this);
        myTestStartedIndicator = tempTestIndicator;
      }
      finally {
        super.tearDown();
      }
    }
  }

  private void enableDebugLogging() {
    List<String> commonCategories = new ArrayList<String>(Arrays.asList("#" + Executor.class.getName(),
                                                                        "#" + GitHandler.class.getName(),
                                                                        GitHandler.class.getName()));
    commonCategories.addAll(getDebugLogCategories());
    TestLoggerFactory.enableDebugLogging(myTestRootDisposable, ArrayUtil.toStringArray(commonCategories));
    myTestStartedIndicator = createTestStartedIndicator();
    LOG.info(myTestStartedIndicator);
  }

  @NotNull
  protected Collection<String> getDebugLogCategories() {
    return Collections.emptyList();
  }

  @Override
  protected void defaultRunBare() throws Throwable {
    try {
      super.defaultRunBare();
    }
    catch (Throwable throwable) {
      if (myTestStartedIndicator != null) {
        TestLoggerFactory.dumpLogToStdout(myTestStartedIndicator);
      }
      throw throwable;
    }
  }

  @NotNull
  private String createTestStartedIndicator() {
    return "Starting " + getClass().getName() + "." + getTestName(false) + Math.random();
  }

  @NotNull
  protected GitRepository createRepository(@NotNull String rootDir) {
    return GitTestUtil.createRepository(myProject, rootDir);
  }

  /**
   * Clones the given source repository into a bare parent.git and adds the remote origin.
   */
  protected void prepareRemoteRepo(@NotNull GitRepository source) {
    final String target = "parent.git";
    final String targetName = "origin";
    Executor.cd(myProjectRoot);
    GitExecutor.git("clone --bare '%s' %s", source.getRoot().getPath(), target);
    GitExecutor.cd(source);
    GitExecutor.git("remote add %s '%s'", targetName, myProjectRoot + "/" + target);
  }

  protected void refresh() {
    myProjectRoot.refresh(false, true);
  }

  protected void doActionSilently(final VcsConfiguration.StandardConfirmation op) {
    AbstractVcsTestCase.setStandardConfirmation(myProject, GitVcs.NAME, op, VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY);
  }

  protected void updateChangeListManager() {
    ChangeListManager changeListManager = ChangeListManager.getInstance(myProject);
    VcsDirtyScopeManager.getInstance(myProject).markEverythingDirty();
    changeListManager.ensureUpToDate(false);
  }

  protected void addSilently() {
    doActionSilently(VcsConfiguration.StandardConfirmation.ADD);
  }

  protected void removeSilently() {
    doActionSilently(VcsConfiguration.StandardConfirmation.REMOVE);
  }

}
