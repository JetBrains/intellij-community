/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.tests;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.testFramework.AbstractVcsTestCase;
import com.intellij.ui.GuiUtils;
import com.intellij.util.ui.UIUtil;
import git4idea.GitVcs;
import git4idea.config.GitVcsApplicationSettings;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * The common ancestor for git test cases which need git executable.
 * These tests can be executed only if git is installed in the system and IDEA_TEST_GIT_EXECUTABLE_PATH targets to the folder which
 * contains git executable.
 * @author Kirill Likhodedov
 */
public abstract class GitTest extends AbstractVcsTestCase {

  public static final String GIT_EXECUTABLE_PATH = "IDEA_TEST_GIT_EXECUTABLE_PATH";

  private static final String GIT_EXECUTABLE = "git";
  protected GitTestRepository myMainRepo;
  private File myProjectDir;

  @BeforeMethod
  protected void setUp() throws Exception {
    // setting git executable
    String exec = System.getenv(GIT_EXECUTABLE_PATH);
    if (exec != null) {
      myClientBinaryPath = new File(exec);
    }
    if (exec == null || !myClientBinaryPath.exists()) {
      final File pluginRoot = new File(PluginPathManager.getPluginHomePath("git4idea"));
      myClientBinaryPath = new File(pluginRoot, "tests/git4idea/tests/data/bin");
    }

    myMainRepo = initRepositories();

    myProjectDir = new File(myMainRepo.getDirFixture().getTempDirPath());
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          initProject(myProjectDir);
          activateVCS(GitVcs.NAME);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });

    final GitVcsApplicationSettings settings = GitVcsApplicationSettings.getInstance();
    File executable = new File(myClientBinaryPath, SystemInfo.isWindows ? GIT_EXECUTABLE + ".exe" : GIT_EXECUTABLE);
    settings.setPathToGit(executable.getPath());

    myTraceClient = true;
    doActionSilently(VcsConfiguration.StandardConfirmation.ADD);
    doActionSilently(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  /**
   * Different implementations for {@link GitSingleUserTest} and {@link GitCollaborativeTest}:
   * create a single or several repositories, which will be used in tests.
   * @return main repository which IDEA project will be bound to.
   */
  protected abstract GitTestRepository initRepositories() throws Exception;

  protected abstract void tearDownRepositories() throws Exception;

  @AfterMethod
  protected void tearDown() throws Exception {
    GuiUtils.runOrInvokeAndWait(new Runnable() {
      @Override
      public void run() {
        try {
          tearDownProject();
          tearDownRepositories();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
  }

  /**
   * Executes the given native Git command with parameters in the given working directory.
   * @param workingDir  working directory where the command will be executed. May be null.
   * @param commandLine command and parameters (e.g. 'status, -m').
   */
  protected ProcessOutput executeCommand(@Nullable File workingDir, String... commandLine) throws IOException {
    return runClient(GIT_EXECUTABLE, null, workingDir, commandLine);
  }

  protected void doActionSilently(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(GitVcs.NAME, op, VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY);
  }

  protected String tos(FilePath fp) {
    return FileUtil.getRelativePath(new File(myMainRepo.getDir().getPath()), fp.getIOFile());
  }

  protected String tos(Change change) {
    switch (change.getType()) {
      case NEW: return "A: " + tos(change.getAfterRevision());
      case DELETED: return "D: " + tos(change.getBeforeRevision());
      case MOVED: return "M: " + tos(change.getBeforeRevision()) + " -> " + tos(change.getAfterRevision());
      case MODIFICATION: return "M: " + tos(change.getAfterRevision());
      default: return "~: " +  tos(change.getBeforeRevision()) + " -> " + tos(change.getAfterRevision());
    }
  }

  protected String tos(ContentRevision revision) {
    return tos(revision.getFile());
  }

  protected String tos(Map<FilePath, Change> changes) {
    StringBuilder stringBuilder = new StringBuilder("[");
    for (Change change : changes.values()) {
      stringBuilder.append(tos(change)).append(", ");
    }
    stringBuilder.append("]");
    return stringBuilder.toString();
  }

}
