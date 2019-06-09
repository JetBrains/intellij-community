// Copyright 2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.test;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.vcs.AbstractJunitVcsTestCase;
import com.intellij.vcsUtil.VcsUtil;
import hg4idea.test.HgExecutor;
import hg4idea.test.HgPlatformTest;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.picocontainer.MutablePicoContainer;
import org.zmlx.hg4idea.HgFile;
import org.zmlx.hg4idea.HgVcs;

import java.io.*;

import static org.junit.Assert.assertTrue;

/**
 * The ancestor of all intellij.vcs.hg test cases.
 *
 * @deprecated Use {@link HgPlatformTest}.
 */
public abstract class HgTest extends AbstractJunitVcsTestCase {

  public static final String HG_EXECUTABLE_PATH = "IDEA_TEST_HG_EXECUTABLE_PATH";
  public static final String HG_EXECUTABLE = "hg";

  // some shortcuts to use in tests
  protected static final String AFILE = "a.txt";
  protected static final String BDIR = "b";
  protected static final String BFILE = "b.txt";
  protected static final String BFILE_PATH = BDIR + File.separator + BFILE;
  protected static final String INITIAL_FILE_CONTENT = "Initial file content";
  protected static final String UPDATED_FILE_CONTENT = "Updated file content";

  protected File myProjectDir; // location of the project repository. Initialized differently in each test: by init or by clone.
  protected HgTestChangeListManager myChangeListManager;
  private HgTestRepository myMainRepo;
  private HgVcs myVcs;

  @Before
  public void setUp() throws Exception {
    // setting hg executable
    String exec = System.getenv(HG_EXECUTABLE_PATH);
    if (exec != null) {
      myClientBinaryPath = new File(exec);
    }
    if (exec == null || !myClientBinaryPath.exists()) {
      final File pluginRoot = new File(PluginPathManager.getPluginHomePath(HgVcs.VCS_NAME));
      myClientBinaryPath = new File(pluginRoot, "testData/bin");
    }

    myMainRepo = initRepositories();
    myProjectDir = new File(myMainRepo.getDirFixture().getTempDirPath());

    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        initProject(myProjectDir, getTestName());
        activateVCS(HgVcs.VCS_NAME);

        myVcs = HgVcs.getInstance(myProject);
        myVcs.getGlobalSettings().setHgExecutable(HgExecutor.getHgExecutable());
      } catch (Exception e) {
        e.printStackTrace();
      }
    });

    myChangeListManager = new HgTestChangeListManager(myProject);
    myTraceClient = true;
    doActionSilently(VcsConfiguration.StandardConfirmation.ADD);
    doActionSilently(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  @After
  public void tearDown() throws Exception {
    EdtTestUtil.runInEdtAndWait(() -> {
      new RunAll(() -> myVcs.getGlobalSettings().setHgExecutable(null),
                 () -> tearDownProject(),
                 () -> tearDownRepositories())
        .run();
    });
  }

  protected abstract HgTestRepository initRepositories() throws Exception;

  protected abstract void tearDownRepositories() throws Exception;

  protected void doActionSilently(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(HgVcs.VCS_NAME, op, VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY);
  }

  protected void doNothingSilently(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(HgVcs.VCS_NAME, op, VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY);
  }

  protected void showConfirmation(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(HgVcs.VCS_NAME, op, VcsShowConfirmationOption.Value.SHOW_CONFIRMATION);
  }

  /**
   * Runs the hg command.
   *
   * @param commandLine the name of the command and its arguments.
   */
  protected ProcessOutput runHgOnProjectRepo(String... commandLine) throws IOException {
    return runHg(myProjectDir, commandLine);
  }

  /**
   * Verifies the status of the file calling native 'hg status' command.
   *
   * @param status status as returned by {@link #added(java.lang.String)} and other methods.
   * @throws IOException
   */
  protected void verifyStatus(String... status) throws IOException {
    verify(runHg(myProjectDir, "status"), status);
}
  /**
   * Calls "hg add ." to add everything to the index.
   */
  protected ProcessOutput addAll() throws IOException {
    return runHg(myProjectDir, "add", ".");
  }

  /**
   * Calls "hg commit -m &lt;commitMessage&gt;" to commit the index.
   */
  protected ProcessOutput commitAll(String commitMessage) throws IOException {
    return runHg(myProjectDir, "commit", "-m", commitMessage);
  }

  protected HgFile getHgFile(String... filepath) {
    File fileToInclude = myProjectDir;
    for (String path : filepath) {
      fileToInclude = new File(fileToInclude, path);
    }
    return new HgFile(myWorkingCopyDir, fileToInclude);
  }

  /**
   * Registers HgMockVcsHelper as the AbstractVcsHelper.
   */
  protected HgMockVcsHelper registerMockVcsHelper() {
    final String key = "com.intellij.openapi.vcs.AbstractVcsHelper";
    final MutablePicoContainer picoContainer = (MutablePicoContainer) myProject.getPicoContainer();
    picoContainer.unregisterComponent(key);
    picoContainer.registerComponentImplementation(key, HgMockVcsHelper.class);
    return (HgMockVcsHelper) AbstractVcsHelper.getInstance(myProject);
  }

  protected VirtualFile makeFile(File file) throws IOException {
    file.createNewFile();
    VcsDirtyScopeManager.getInstance(myProject).fileDirty(myWorkingCopyDir);
    refreshVfs();
    return VcsUtil.getVirtualFile(file);
  }

  /**
   * Executes the given native Mercurial command with parameters in the given working directory.
   * @param workingDir  working directory where the command will be executed. May be null.
   * @param commandLine command and parameters (e.g. 'status, -m').
   */
  protected ProcessOutput runHg(@Nullable File workingDir, String... commandLine) throws IOException {
    return createClientRunner().runClient(HG_EXECUTABLE, null, workingDir, commandLine);
  }

  protected File fillFile(File aParentDir, String[] filePath, String fileContents) throws FileNotFoundException {
    File parentDir = aParentDir;
    for (int i = 0; i < filePath.length - 1; i++) {
      File current = new File(parentDir, filePath[i]);
      if (!current.exists() || !current.isDirectory()) {
        assertTrue(current.mkdir());
      }
      parentDir = current;
    }
    File outputFile = new File(parentDir, filePath[filePath.length - 1]);

    PrintStream printer = new PrintStream(new FileOutputStream(outputFile));
    printer.print(fileContents);
    printer.close();

    return outputFile;
  }

}
