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

import com.intellij.ide.highlighter.ProjectFileType;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.vcs.AbstractVcsTestCase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThrowableRunnable;
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

import java.io.File;
import java.io.IOException;
import java.util.*;

public abstract class GitPlatformTest extends PlatformTestCase {

  protected static final Logger LOG = Logger.getInstance(GitPlatformTest.class);

  protected VirtualFile myProjectRoot;
  protected String myProjectPath;
  protected GitRepositoryManager myGitRepositoryManager;
  protected GitVcsSettings myGitSettings;
  protected GitPlatformFacade myPlatformFacade;
  protected Git myGit;
  protected GitVcs myVcs;

  protected TestDialogManager myDialogManager;
  protected TestVcsNotifier myVcsNotifier;

  protected File myTestRoot;

  private String myTestStartedIndicator;

  @Override
  protected void setUp() throws Exception {
    myTestRoot = new File(FileUtil.getTempDirectory(), "testRoot");
    myFilesToDelete.add(myTestRoot);

    checkTestRootIsEmpty(myTestRoot);

    EdtTestUtil.runInEdtAndWait(new ThrowableRunnable<Throwable>() {
      @Override
      public void run() throws Exception {
        GitPlatformTest.super.setUp();
      }
    });

    enableDebugLogging();

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

  private static void checkTestRootIsEmpty(@NotNull File testRoot) {
    File[] files = testRoot.listFiles();
    if (files != null && files.length > 0) {
      LOG.warn("Test root was not cleaned up during some previous test run. " +
               "testRoot: " + testRoot + ", files: " + Arrays.toString(files));
      for (File file : files) {
        LOG.assertTrue(FileUtil.delete(file));
      }
    }
  }

  @Override
  protected File getIprFile() throws IOException {
    File projectRoot = new File(myTestRoot, "project");
    return FileUtil.createTempFile(projectRoot, getName() + "_", ProjectFileType.DOT_DEFAULT_EXTENSION);
  }

  @Override
  protected void setUpModule() {
    // we don't need a module in Git tests
  }

  @Override
  protected boolean isRunInEdt() {
    return false;
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
    }
    finally {
      try {
        EdtTestUtil.runInEdtAndWait(new ThrowableRunnable<Throwable>() {
          @Override
          public void run() throws Exception {
            GitPlatformTest.super.tearDown();
          }
        });
      }
      finally {
        if (myAssertionsInTestDetected) {
          TestLoggerFactory.dumpLogToStdout(myTestStartedIndicator);
        }
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
    VfsUtil.markDirtyAndRefresh(false, true, false, myProjectRoot);
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
