// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import org.jetbrains.annotations.NotNull;
import org.junit.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class WindowsCaseSensitivityTest extends BareTestFixtureTestCase {
  private static final Logger LOG = Logger.getInstance(WindowsCaseSensitivityTest.class);
  @Rule public TempDirectory myTempDir = new TempDirectory();

  @Before
  public void setUp() {
    IoTestUtil.assumeWindows();
    // under windows it requires admin privileges which we need here
    IoTestUtil.assumeSymLinkCreationIsSupported();
    Assume.assumeTrue("fsutil.exe doesn't work (seems WSL is not enabled)", doesWindowsFSUtilWork());
  }

  // true if "fsutil file setCaseSensitiveInfo" is able to change the directory case-sensitivity
  // usually Windows requires WSL subsystem to be enabled for that
  private static boolean doesWindowsFSUtilWork() {
    String system32 = getWindowsSystem32();
    Assert.assertNotNull(system32);
    // "fsutil file setCaseSensitiveInfo" works only when WSL subsystem is enabled
    File wsl = new File(system32, "wsl.exe");
    File fsutil = new File(system32, "fsutil.exe");
    if (!wsl.exists() || !wsl.canExecute() || !fsutil.exists() || !fsutil.canExecute()) {
      System.err.println(wsl +" exists: "+wsl.exists()+"; executable: "+wsl.canExecute()+"; "+fsutil+" exists: "+fsutil.exists()+"; executable: "+fsutil.canExecute());
      return false;
    }
    return true;
  }

  @Test
  public void testCaseSensitiveDirectoryUnderWindowsMustBeDetected() throws Exception {
    File dir = myTempDir.newDirectory();
    VirtualFile vDir = SymlinkHandlingTest.refreshAndFind(dir, dir);
    Assert.assertNotNull(vDir);
    Assert.assertFalse(vDir.isCaseSensitive());
    String system32 = getWindowsSystem32();
    Assert.assertNotNull(system32);
    Assert.assertTrue(new File(system32 + "\\fsutil.exe").exists());
    exec(system32+"\\fsutil.exe", "file", "setCaseSensitiveInfo", "\"" + dir.getPath() + "\"", "enable");
    VirtualFile readme = createChildData(vDir, "readme.txt");
    Assert.assertTrue(readme.isCaseSensitive());
  }

  @Test
  public void testFSUtilWorks_tempTest() throws Exception {
    File dir = myTempDir.newDirectory();
    VirtualFile vDir = SymlinkHandlingTest.refreshAndFind(dir, dir);
    Assert.assertNotNull(vDir);
    Assert.assertFalse(vDir.isCaseSensitive());
    String system32 = getWindowsSystem32();
    Assert.assertNotNull(system32);
    Assert.assertTrue(new File(system32 + "\\fsutil.exe").exists());
    exec(system32+"\\fsutil.exe", "file", "setCaseSensitiveInfo", "\"" + dir.getPath() + "\"", "enable");
    VirtualFile readme = createChildData(vDir, "readme.txt");
    VirtualFile README = createChildData(vDir, "README.TXT");
    LOG.debug(((VirtualFileSystemEntry)readme).getId()+", "+((VirtualFileSystemEntry)README).getId());
    Assert.assertTrue(readme.isCaseSensitive());
    Assert.assertTrue(README.isCaseSensitive());
  }

  private static String getWindowsSystem32() {
    String windir = System.getenv().get("windir");
    return StringUtil.isEmpty(windir) ? null : windir+"\\System32";
  }

  protected static @NotNull VirtualFile createChildData(@NotNull VirtualFile dir, @NotNull String name) {
    try {
      return WriteAction.computeAndWait(() -> dir.createChildData(null, name));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static void exec(String... command) throws Exception {
    Process process = new ProcessBuilder(command).start();
    if (!process.waitFor(100, TimeUnit.SECONDS)) {
      Assert.fail("Too long run");
    }
    int exitValue = process.exitValue();
    Assert.assertEquals(0, exitValue);
    String error = FileUtil.loadTextAndClose(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
    LOG.debug("error :\n");
    LOG.debug(error);
    String out = FileUtil.loadTextAndClose(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
    LOG.debug("output :\n");
    LOG.debug(out);
  }
}
