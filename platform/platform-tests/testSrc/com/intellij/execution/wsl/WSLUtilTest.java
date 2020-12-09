// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.LightPlatform4TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assume.assumeTrue;

public class WSLUtilTest extends LightPlatform4TestCase {

  @Nullable
  private WSLDistribution myWSL;

  public WSLUtilTest() {
    super();
  }

  @Before
  public void setUp0() throws Exception {
    if (WSLUtil.hasAvailableDistributions()) {
      myWSL = WSLUtil.getAvailableDistributions().get(0);
    }
  }

  private static void assumeWSLAvailable() {
    assumeTrue("WSL unavailable", WSLUtil.hasAvailableDistributions());
  }

  @Test
  public void testWslToWinPath() {
    assumeWSLAvailable();

    assertWslPath("/mnt/cd", null);
    assertWslPath("/mnt", null);
    assertWslPath("", null);
    assertWslPath("/mnt//test", null);
    assertWslPath("/mnt/1/test", null);
    assertWslPath("/mnt/c", "C:");
    assertWslPath("/mnt/x/", "X:\\");

    assertWslPath("/mnt/c/temp/foo", "C:\\temp\\foo");
    assertWslPath("/mnt/c/temp/KeepCase", "C:\\temp\\KeepCase");
    assertWslPath("/mnt/c/name with spaces/another name with spaces", "C:\\name with spaces\\another name with spaces");
    assertWslPath("/mnt/c/юникод", "C:\\юникод");
  }

  @Test
  public void testWinToWslPath() {
    assumeWSLAvailable();

    assertWinPath("C:\\foo", "/mnt/c/foo");
    assertWinPath("C:\\temp\\KeepCase", "/mnt/c/temp/KeepCase");
    assertWinPath("?:\\temp\\KeepCase", null);
    assertWinPath("c:c", null);
  }

  @Test
  public void testPaths() {
    assumeWSLAvailable();

    final String originalWinPath = "C:\\usr\\something\\bin\\gcc";
    final String winPath = myWSL.getWindowsPath(myWSL.getWslPath(originalWinPath));
    assertEquals(originalWinPath, winPath);

    final String originalWslPath = "/mnt/c/usr/bin/gcc";
    final String wslPath = myWSL.getWslPath(myWSL.getWindowsPath(originalWslPath));
    assertEquals(originalWslPath, wslPath);
  }

  @Test
  public void testResolveSymlink() throws Exception {
    assumeWSLAvailable();

    final File winFile = FileUtil.createTempFile("the_file.txt", null);
    final File winSymlink = new File(new File(FileUtil.getTempDirectory()), "sym_link");

    try {
      final String file = myWSL.getWslPath(winFile.getPath());
      final String symlink = myWSL.getWslPath(winSymlink.getPath());
      mkSymlink(file, symlink);

      final String resolved = myWSL.getWindowsPath(myWSL.resolveSymlink(symlink));
      assertTrue(FileUtil.exists(resolved));
      assertTrue(winFile.getPath().equalsIgnoreCase(resolved));
    }
    finally {
      FileUtil.delete(winFile);
      FileUtil.delete(winSymlink);
    }
  }

  private void assertWinPath(@NotNull String winPath, @Nullable String wslPath) {
    assertEquals(wslPath, myWSL.getWslPath(winPath));
  }

  private void assertWslPath(@NotNull String wslPath, @Nullable String winPath) {
    assertEquals(winPath, myWSL.getWindowsPath(wslPath));
  }

  private void mkSymlink(@NotNull String file, @NotNull String symlink) throws Exception {
    final GeneralCommandLine cl = new GeneralCommandLine();
    cl.setExePath("ln");
    cl.addParameters("-s", file, symlink);

    final GeneralCommandLine cmd = myWSL.patchCommandLine(cl, null, null, false);
    final CapturingProcessHandler process = new CapturingProcessHandler(cmd);
    final ProcessOutput output = WSLUtil.addInputCloseListener(process).runProcess(10_000);
    assertFalse(output.isTimeout());
    assertEquals(0, output.getExitCode());
  }
}