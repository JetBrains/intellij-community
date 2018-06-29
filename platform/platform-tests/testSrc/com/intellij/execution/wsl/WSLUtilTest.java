// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class WSLUtilTest {

  @Nullable
  private WSLDistribution myLegacyWSL;

  @Before
  public void setUp() throws Exception {
    myLegacyWSL = WSLDistributionLegacy.getInstance();
  }

  @Test
  public void testWslToWinPath() {
    assumeTrue(myLegacyWSL != null);

    assertWslPath("/usr/something/include", "%LOCALAPPDATA%\\lxss\\rootfs\\usr\\something\\include", true);
    assertWslPath("/usr/something/bin/gcc", "%LOCALAPPDATA%\\lxss\\rootfs\\usr\\something\\bin\\gcc", true);

    assertWslPath("/mnt/cd", null, false);
    assertWslPath("/mnt", null, false);
    assertWslPath("", null, false);
    assertWslPath("/mnt//test", null, false);
    assertWslPath("/mnt/1/test", null, false);
    assertWslPath("/mnt/c", "c:", false);
    assertWslPath("/mnt/x/", "x:\\", false);

    assertWslPath("/mnt/c/temp/foo", "c:\\temp\\foo", false);
    assertWslPath("/mnt/c/temp/KeepCase", "c:\\temp\\KeepCase", false);
    assertWslPath("/mnt/c/name with spaces/another name with spaces", "c:\\name with spaces\\another name with spaces", false);
    assertWslPath("/mnt/c/юникод", "c:\\юникод", false);
  }

  @Test
  public void testWinToWslPath() {
    assumeTrue(myLegacyWSL != null);

    assertWinPath("c:\\foo", "/mnt/c/foo");
    assertWinPath("c:\\temp\\KeepCase", "/mnt/c/temp/KeepCase");
    assertWinPath("?:\\temp\\KeepCase", null);

    assertWinPath("%LOCALAPPDATA%\\lxss\\rootfs\\usr\\something\\include", "/usr/something/include");
    assertWinPath("%LOCALAPPDATA%\\lxss\\rootfs\\usr\\something\\bin\\gcc", "/usr/something/bin/gcc");
  }

  @Test
  public void testPaths() {
    assumeTrue(myLegacyWSL != null);

    final String originalWinPath = "c:\\usr\\something\\bin\\gcc";
    final String winPath = myLegacyWSL.getWindowsPath(myLegacyWSL.getWslPath(originalWinPath));
    assertEquals(originalWinPath, winPath);

    final String originalWslPath = "/usr/bin/gcc";
    final String wslPath = myLegacyWSL.getWslPath(myLegacyWSL.getWindowsPath(originalWslPath));
    assertEquals(originalWslPath, wslPath);
  }

  @Test
  public void testResolveSymlink() throws Exception {
    assumeTrue(myLegacyWSL != null);

    final File winFile = FileUtil.createTempFile("the_file.txt", null);
    final File winSymlink = new File(new File(FileUtil.getTempDirectory()), "sym_link");

    try {
      final String file = myLegacyWSL.getWslPath(winFile.getPath());
      final String symlink = myLegacyWSL.getWslPath(winSymlink.getPath());
      mkSymlink(file, symlink);

      final String resolved = myLegacyWSL.getWindowsPath(myLegacyWSL.resolveSymlink(symlink));
      assertTrue(FileUtil.exists(resolved));
      assertTrue(winFile.getPath().equalsIgnoreCase(resolved));
    }
    finally {
      FileUtil.delete(winFile);
      FileUtil.delete(winSymlink);
    }
  }

  private void assertWinPath(@NotNull String winPath, @Nullable String wslPath) {
    assertEquals(wslPath, myLegacyWSL.getWslPath(prepare(winPath)));
  }

  private void assertWslPath(@NotNull String wslPath, @Nullable String winPath, boolean forLegacyWSL) {
    String windowsPath = forLegacyWSL ? myLegacyWSL.getWindowsPath(wslPath) : WSLUtil.getWindowsPath(wslPath);
    assertEquals(prepare(winPath), windowsPath);
  }

  @Nullable
  private static String prepare(@Nullable String path) {
    if (path != null && path.startsWith("%LOCALAPPDATA%")) {
      final String localappdata = System.getenv().get("LOCALAPPDATA");
      path = localappdata + path.substring("%LOCALAPPDATA%".length());
    }
    return path;
  }

  private void mkSymlink(@NotNull String file, @NotNull String symlink) throws Exception {
    final GeneralCommandLine cl = new GeneralCommandLine();
    cl.setExePath("ln");
    cl.addParameters("-s", file, symlink);

    final GeneralCommandLine cmd = myLegacyWSL.patchCommandLine(cl, null, null, false);
    final CapturingProcessHandler process = new CapturingProcessHandler(cmd);
    final ProcessOutput output = WSLUtil.addInputCloseListener(process).runProcess(10_000);
    assertFalse(output.isTimeout());
    assertEquals(0, output.getExitCode());
  }
}