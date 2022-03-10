// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public final class WSLUtilTest extends WslTestBase {
  @Rule
  public final TempDirectory myTempDirectory = new TempDirectory();

  @Test
  public void testWslToWinPath() {
    var wsl = getWsl();
    assertEquals("\\\\wsl$\\" + wsl.getMsId() + "\\mnt\\cd", wsl.getWindowsPath("/mnt/cd"));
    assertEquals("\\\\wsl$\\" + wsl.getMsId() + "\\mnt", wsl.getWindowsPath("/mnt"));
    assertEquals("\\\\wsl$\\" + wsl.getMsId(), wsl.getWindowsPath(""));
    assertEquals("\\\\wsl$\\" + wsl.getMsId() + "\\mnt\\test", wsl.getWindowsPath("/mnt//test"));
    assertEquals("\\\\wsl$\\" + wsl.getMsId() + "\\mnt\\1\\test", wsl.getWindowsPath("/mnt/1/test"));

    assertEquals("C:", wsl.getWindowsPath("/mnt/c"));
    assertEquals("\\\\wsl$\\" + wsl.getMsId() + "\\mnt\\", wsl.getWindowsPath("/mnt/"));
    assertEquals("\\\\wsl$\\" + wsl.getMsId() + "\\mnt", wsl.getWindowsPath("/mnt"));
    assertEquals("\\\\wsl$\\" + wsl.getMsId() + "\\", wsl.getWindowsPath("/"));
    assertEquals("X:\\", wsl.getWindowsPath("/mnt/x/"));
    assertEquals("C:", wsl.getWindowsPath("/mnt/C"));

    assertEquals("C:\\temp\\foo", wsl.getWindowsPath("/mnt/c/temp/foo"));
    assertEquals("C:\\temp\\KeepCase", wsl.getWindowsPath("/mnt/c/temp/KeepCase"));
    assertEquals("C:\\name with spaces\\another name with spaces", wsl.getWindowsPath("/mnt/c/name with spaces/another name with spaces"));
    //noinspection NonAsciiCharacters
    assertEquals("C:\\юникод", wsl.getWindowsPath("/mnt/c/юникод"));
  }

  @Test
  public void testWinToWslPath() {
    var wsl = getWsl();
    assertEquals("/mnt/c/foo", wsl.getWslPath("C:\\foo"));
    assertEquals("/mnt/c/temp/KeepCase", wsl.getWslPath("C:\\temp\\KeepCase"));
    assertNull(wsl.getWslPath("?:\\temp\\KeepCase"));
    assertNull(wsl.getWslPath("c:c"));
  }

  @Test
  public void testPaths() {
    var wsl = getWsl();
    String originalWinPath = "C:\\usr\\something\\bin\\gcc";
    assertEquals(originalWinPath, wsl.getWindowsPath(wsl.getWslPath(originalWinPath)));

    String originalWslPath = "/mnt/c/usr/bin/gcc";
    assertEquals(originalWslPath, wsl.getWslPath(wsl.getWindowsPath(originalWslPath)));
  }

  @Test
  public void testResolveSymlink() throws Exception {
    var wsl = getWsl();
    File winFile = myTempDirectory.newFile("the_file.txt");
    File winSymlink = new File(myTempDirectory.getRoot(), "sym_link");

    String file = wsl.getWslPath(winFile.getPath());
    String symlink = wsl.getWslPath(winSymlink.getPath());
    mkSymlink(file, symlink);

    String resolved = wsl.getWindowsPath(wsl.resolveSymlink(symlink));
    assertNotNull(resolved);
    assertTrue(new File(resolved).exists());
    assertTrue(winFile.getPath().equalsIgnoreCase(resolved));
  }

  private void mkSymlink(String file, String symlink) throws Exception {
    var wsl = getWsl();
    GeneralCommandLine cmd = wsl.patchCommandLine(new GeneralCommandLine("ln", "-s", file, symlink), null, new WSLCommandLineOptions());
    ProcessOutput output = new CapturingProcessHandler(cmd).runProcess(10_000);
    assertEquals(0, output.getExitCode());
  }
}
