// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.vfs.impl.wsl.WslConstants;
import com.intellij.testFramework.fixtures.TestFixtureRule;
import com.intellij.testFramework.rules.TempDirectory;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import java.io.File;

import static org.junit.Assert.*;

public final class WSLUtilTest {
  private static final TestFixtureRule appRule = new TestFixtureRule();
  private static final WslRule wslRule = new WslRule();
  @ClassRule public static final RuleChain ruleChain = RuleChain.outerRule(appRule).around(wslRule);

  @Rule public final TempDirectory tempDirectory = new TempDirectory();

  @Test
  public void testUncPrefix() {
    Assertions.assertThat(WSLUtil.getUncPrefix()).isIn(WslConstants.UNC_PREFIX, "\\\\wsl.localhost\\");
  }

  @Test
  public void testWslToWinPath() {
    var wsl = wslRule.getWsl();
    assertEquals(WSLUtil.getUncPrefix() + wsl.getMsId() + "\\mnt\\cd", wsl.getWindowsPath("/mnt/cd"));
    assertEquals(WSLUtil.getUncPrefix() + wsl.getMsId()+ "\\mnt", wsl.getWindowsPath("/mnt"));
    assertEquals(WSLUtil.getUncPrefix() + wsl.getMsId(), wsl.getWindowsPath(""));
    assertEquals(WSLUtil.getUncPrefix() + wsl.getMsId() + "\\mnt\\test", wsl.getWindowsPath("/mnt//test"));
    assertEquals(WSLUtil.getUncPrefix() + wsl.getMsId() + "\\mnt\\1\\test", wsl.getWindowsPath("/mnt/1/test"));

    assertEquals("C:", wsl.getWindowsPath("/mnt/c"));
    assertEquals(WSLUtil.getUncPrefix() + wsl.getMsId() + "\\mnt\\", wsl.getWindowsPath("/mnt/"));
    assertEquals(WSLUtil.getUncPrefix() + wsl.getMsId() + "\\mnt", wsl.getWindowsPath("/mnt"));
    assertEquals(WSLUtil.getUncPrefix() + wsl.getMsId() + "\\", wsl.getWindowsPath("/"));
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
    var wsl = wslRule.getWsl();
    assertEquals("/mnt/c/foo", wsl.getWslPath("C:\\foo"));
    assertEquals("/mnt/c/temp/KeepCase", wsl.getWslPath("C:\\temp\\KeepCase"));
    assertNull(wsl.getWslPath("?:\\temp\\KeepCase"));
    assertNull(wsl.getWslPath("c:c"));
  }

  @Test
  public void testPaths() {
    var wsl = wslRule.getWsl();
    String originalWinPath = "C:\\usr\\something\\bin\\gcc";
    assertEquals(originalWinPath, wsl.getWindowsPath(wsl.getWslPath(originalWinPath)));

    String originalWslPath = "/mnt/c/usr/bin/gcc";
    assertEquals(originalWslPath, wsl.getWslPath(wsl.getWindowsPath(originalWslPath)));
  }

  @Test
  public void testResolveSymlink() throws Exception {
    var wsl = wslRule.getWsl();
    File winFile = tempDirectory.newFile("the_file.txt");
    File winSymlink = new File(tempDirectory.getRoot(), "sym_link");

    String file = wsl.getWslPath(winFile.getPath());
    String symlink = wsl.getWslPath(winSymlink.getPath());
    mkSymlink(file, symlink);

    String resolved = wsl.getWindowsPath(wsl.resolveSymlink(symlink));
    assertNotNull(resolved);
    assertTrue(new File(resolved).exists());
    assertTrue(winFile.getPath().equalsIgnoreCase(resolved));
  }

  private static void mkSymlink(String file, String symlink) throws Exception {
    var wsl = wslRule.getWsl();
    GeneralCommandLine cmd = wsl.patchCommandLine(new GeneralCommandLine("ln", "-s", file, symlink), null, new WSLCommandLineOptions());
    ProcessOutput output = new CapturingProcessHandler(cmd).runProcess(10_000);
    assertEquals(0, output.getExitCode());
  }
}
