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
package org.zmlx.hg4idea;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.AbstractVcsTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.vcsUtil.VcsUtil;
import org.testng.annotations.BeforeMethod;
import org.zmlx.hg4idea.org.zmlx.hg4idea.test.TestChangeListManager;

import java.io.*;

import static org.testng.Assert.assertTrue;

/**
 * The ancestor of all hg4idea test cases.
 */
public abstract class AbstractHgTestCase extends AbstractVcsTestCase {

  public static final String HG_EXECUTABLE_PATH = "IDEA_TEST_HG_EXECUTABLE_PATH";

  protected File myProjectRepo;
  protected TempDirTestFixture myTempDirTestFixture;
  protected TestChangeListManager myChangeListManager;

  // some shortcuts to use in tests
  protected static final String AFILE = "a.txt";
  protected static final String BDIR = "b";
  protected static final String BFILE = "b.txt";
  protected static final String BFILE_PATH = BDIR + File.separator + BFILE;
  protected static final String FILE_CONTENT = "Sample file content.";
  protected static final String FILE_CONTENT_2 = "some other file content";

  @BeforeMethod
  protected void setUp() throws Exception {
    setHGExecutablePath();

    myTempDirTestFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    myTempDirTestFixture.setUp();
    myProjectRepo = new File(myTempDirTestFixture.getTempDirPath());

    ProcessOutput processOutput = runHg(myProjectRepo, "init");
    verify(processOutput);
    initProject(myProjectRepo);
    activateVCS(HgVcs.VCS_NAME);

    myChangeListManager = new TestChangeListManager(myProject);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  protected void setHGExecutablePath() {
    // setting hg executable
    String exec = System.getenv(HG_EXECUTABLE_PATH);
    System.out.println("exec: " + exec);
    if (exec != null) {
      System.out.println("Using external");
      myClientBinaryPath = new File(exec);
    }
    if (exec == null || !myClientBinaryPath.exists()) {
      System.out.println("Using checked in");
      File pluginRoot = new File(PluginPathManager.getPluginHomePath(HgVcs.VCS_NAME));
      myClientBinaryPath = new File(pluginRoot, "testData/bin");
    }

    HgVcs.setTestHgExecutablePath(myClientBinaryPath.getPath());
  }

  /**
   * Runs the hg command.
   * @param commandLine the name of the command and its arguments.
   */
  protected ProcessOutput runHgOnProjectRepo(String... commandLine) throws IOException {
    return runHg(myProjectRepo, commandLine);
  }

  /**
   * Calls "hg add ." to add everything to the index.
   */
  protected ProcessOutput addAll() throws IOException {
    return runHgOnProjectRepo("add", ".");
  }

  /**
   * Calls "hg commit -m &lt;commitMessage&gt;" to commit the index.
   */
  protected ProcessOutput commitAll(String commitMessage) throws IOException {
    return runHgOnProjectRepo("commit", "-m", commitMessage);
  }

  protected HgFile getHgFile(String... filepath) {
    File fileToInclude = myProjectRepo;
    for (String path : filepath) {
      fileToInclude = new File(fileToInclude, path);
    }
    return new HgFile(myWorkingCopyDir, fileToInclude);
  }

  protected void enableSilentOperation(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(
      HgVcs.VCS_NAME, op, VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY
    );
  }

  protected void disableSilentOperation(final VcsConfiguration.StandardConfirmation op) {
    setStandardConfirmation(
      HgVcs.VCS_NAME, op, VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY
    );
  }

  protected VirtualFile makeFile(File file) throws IOException {
    file.createNewFile();
    VcsDirtyScopeManager.getInstance(myProject).fileDirty(myWorkingCopyDir);
    LocalFileSystem.getInstance().refresh(false);
    return VcsUtil.getVirtualFile(file);
  }

  protected ProcessOutput runHg(File aHgRepository, String... commandLine) throws IOException {
    String exe = SystemInfo.isWindows ? "hg.exe" : "hg";
    return runClient(exe, null, aHgRepository, commandLine);
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

  /**
   * Verifies the status of the file calling native 'hg status' command.
   * @param status status as returned by {@link #added(java.lang.String)} and other methods.
   * @throws IOException
   */
  protected void verifyStatus(String... status) throws IOException {
    verify(runHgOnProjectRepo("status"), status);
  }

  public static String added(String... path) {
    return "A " + path(path);
  }

  public static String removed(String... path) {
    return "R " + path(path);
  }

  public static String unknown(String... path) {
    return "? " + path(path);
  }

  public static String modified(String... path) {
    return "M " + path(path);
  }

  public static String missing(String... path) {
    return "! " + path(path);
  }

  public static String path(String... line) {
    StringBuilder builder = new StringBuilder();

    int linePartCount = line.length;

    for (int i = 0; i < linePartCount; i++) {
      String linePart = line[i];
      builder.append(linePart);

      if (i < linePartCount - 1) {
        builder.append(File.separator);
      }
    }

    return builder.toString();
  }
}
