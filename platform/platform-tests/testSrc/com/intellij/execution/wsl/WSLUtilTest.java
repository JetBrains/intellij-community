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

    assertWslPath("/usr/something/include", "%LOCALAPPDATA%\\lxss\\rootfs\\usr\\something\\include");
    assertWslPath("/usr/something/bin/gcc", "%LOCALAPPDATA%\\lxss\\rootfs\\usr\\something\\bin\\gcc");

    assertWslPath("/mnt/c", "c:\\");
    assertWslPath("/mnt/x/", "x:\\");

    assertWslPath("/mnt/c/temp/foo", "c:\\temp\\foo");
    assertWslPath("/mnt/c/temp/KeepCase", "c:\\temp\\KeepCase");
    assertWslPath("/mnt/c/name with spaces/another name with spaces", "c:\\name with spaces\\another name with spaces");
    assertWslPath("/mnt/c/юникод", "c:\\юникод");
  }

  @Test
  public void testWinToWslPath() {
    assumeTrue(myLegacyWSL != null);

    assertWinPath("c:\\foo", "/mnt/c/foo");
    assertWinPath("c:\\temp\\KeepCase", "/mnt/c/temp/KeepCase");

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

  private void assertWinPath(@NotNull String winPath, @NotNull String wslPath) {
    assertEquals(wslPath, myLegacyWSL.getWslPath(prepare(winPath)));
  }

  private void assertWslPath(@NotNull String wslPath, @NotNull String winPath) {
    assertEquals(prepare(winPath), myLegacyWSL.getWindowsPath(wslPath));
  }

  private static String prepare(@NotNull String path) {
    if (path.startsWith("%LOCALAPPDATA%")) {
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