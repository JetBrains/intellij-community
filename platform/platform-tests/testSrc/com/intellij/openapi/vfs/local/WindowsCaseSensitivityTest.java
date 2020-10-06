// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.io.SuperUserStatus;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static com.intellij.openapi.util.io.IoTestUtil.assumeWindows;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class WindowsCaseSensitivityTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory myTempDir = new TempDirectory();

  @BeforeClass
  public static void setUp() {
    assumeWindows();
    assumeTrue("'fsutil.exe' needs elevated privileges to work", SuperUserStatus.isSuperUser());
    assumeTrue("'fsutil.exe' not found in %Path%", PathEnvironmentVariableUtil.findInPath("fsutil.exe") != null);
    assumeTrue("'wsl.exe' not found in %Path% (needed for 'setCaseSensitiveInfo')", PathEnvironmentVariableUtil.findInPath("wsl.exe") != null);
  }

  @Test
  public void testCaseSensitiveDirectoryUnderWindowsMustBeDetected() throws Exception {
    File dir = myTempDir.newDirectory();
    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    assertNotNull(vDir);
    assertFalse(vDir.isCaseSensitive());
    exec("fsutil.exe", "file", "setCaseSensitiveInfo", dir.getPath(), "enable");
    VirtualFile readme = createChildData(vDir, "readme.txt");
    assertTrue(readme.isCaseSensitive());
  }

  @Test
  public void testFSUtilWorks_tempTest() throws Exception {
    File dir = myTempDir.newDirectory();
    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir);
    assertNotNull(vDir);
    assertFalse(vDir.isCaseSensitive());
    exec("fsutil.exe", "file", "setCaseSensitiveInfo", dir.getPath(), "enable");
    VirtualFile readme = createChildData(vDir, "readme.txt");
    VirtualFile README = createChildData(vDir, "README.TXT");
    assertNotEquals(((VirtualFileSystemEntry)readme).getId(), ((VirtualFileSystemEntry)README).getId());
    assertTrue(readme.isCaseSensitive());
    assertTrue(README.isCaseSensitive());
  }

  protected static VirtualFile createChildData(VirtualFile dir, String name) throws IOException {
    return WriteAction.computeAndWait(() -> dir.createChildData(null, name));
  }

  private static void exec(String... command) throws Exception {
    GeneralCommandLine cmd = new GeneralCommandLine(command).withRedirectErrorStream(true);
    ProcessOutput output = ExecUtil.execAndGetOutput(cmd, 60_000);
    if (output.getExitCode() != 0 || output.isTimeout()) {
      fail("failed: " + cmd + '\n' +
           "exit code: " + output.getExitCode() + " timeout: " + output.isTimeout() + " output:\n" +
           output.getStdout().trim());
    }
  }
}
