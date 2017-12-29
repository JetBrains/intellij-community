// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.execution;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.wsl.WSLUtil;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;

import static com.intellij.execution.wsl.WSLDistributionLegacy.LEGACY_WSL;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class WSLUtilTest {

  @Test
  public void testWslToWinPath() {
    assumeTrue(LEGACY_WSL.isAvailable());

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
    assumeTrue(LEGACY_WSL.isAvailable());

    assertWinPath("c:\\foo", "/mnt/c/foo");
    assertWinPath("c:\\temp\\KeepCase", "/mnt/c/temp/KeepCase");

    assertWinPath("%LOCALAPPDATA%\\lxss\\rootfs\\usr\\something\\include", "/usr/something/include");
    assertWinPath("%LOCALAPPDATA%\\lxss\\rootfs\\usr\\something\\bin\\gcc", "/usr/something/bin/gcc");
  }

  @Test
  public void testPaths() {
    assumeTrue(LEGACY_WSL.isAvailable());

    final String originalWinPath = "c:\\usr\\something\\bin\\gcc";
    final String winPath = LEGACY_WSL.getWindowsPath(LEGACY_WSL.getWslPath(originalWinPath));
    assertEquals(originalWinPath, winPath);

    final String originalWslPath = "/usr/bin/gcc";
    final String wslPath = LEGACY_WSL.getWslPath(LEGACY_WSL.getWindowsPath(originalWslPath));
    assertEquals(originalWslPath, wslPath);
  }

  @Test
  public void testResolveSymlink() throws Exception {
    assumeTrue(LEGACY_WSL.isAvailable());

    final File winFile = FileUtil.createTempFile("the_file.txt", null);
    final File winSymlink = new File(new File(FileUtil.getTempDirectory()), "sym_link");

    try {
      final String file = LEGACY_WSL.getWslPath(winFile.getPath());
      final String symlink = LEGACY_WSL.getWslPath(winSymlink.getPath());
      mkSymlink(file, symlink);

      final String resolved = LEGACY_WSL.getWindowsPath(LEGACY_WSL.resolveSymlink(symlink));
      assertTrue(FileUtil.exists(resolved));
      assertTrue(winFile.getPath().equalsIgnoreCase(resolved));
    }
    finally {
      FileUtil.delete(winFile);
      FileUtil.delete(winSymlink);
    }
  }

  private static void assertWinPath(@NotNull String winPath, @NotNull String wslPath) {
    assertEquals(wslPath, LEGACY_WSL.getWslPath(prepare(winPath)));
  }

  private static void assertWslPath(@NotNull String wslPath, @NotNull String winPath) {
    assertEquals(prepare(winPath), LEGACY_WSL.getWindowsPath(wslPath));
  }

  private static String prepare(@NotNull String path) {
    if (path.startsWith("%LOCALAPPDATA%")) {
      final String localappdata = System.getenv().get("LOCALAPPDATA");
      path = localappdata + path.substring("%LOCALAPPDATA%".length());
    }
    return path;
  }

  private static void mkSymlink(@NotNull String file, @NotNull String symlink) throws Exception {
    final GeneralCommandLine cl = new GeneralCommandLine();
    cl.setExePath("ln");
    cl.addParameters("-s", file, symlink);

    final GeneralCommandLine cmd = LEGACY_WSL.patchCommandLine(cl, null, null, false);
    final CapturingProcessHandler process = new CapturingProcessHandler(cmd);
    final ProcessOutput output = WSLUtil.addInputCloseListener(process).runProcess(10_000);
    assertFalse(output.isTimeout());
    assertEquals(0, output.getExitCode());
  }
}