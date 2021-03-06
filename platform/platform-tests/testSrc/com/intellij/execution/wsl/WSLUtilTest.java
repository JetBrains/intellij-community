// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.*;

import java.io.File;
import java.util.List;

import static com.intellij.openapi.util.io.IoTestUtil.assumeWindows;
import static com.intellij.openapi.util.io.IoTestUtil.assumeWslPresence;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class WSLUtilTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory tempDir = new TempDirectory();

  private static NullableLazyValue<WSLDistribution> WSL = NullableLazyValue.createValue(() -> {
    List<WSLDistribution> distributions = WslDistributionManager.getInstance().getInstalledDistributions();
    if (distributions.isEmpty()) return null;
    WSLDistribution distribution = distributions.get(0);
    if (distribution instanceof WSLDistributionLegacy || !IoTestUtil.reanimateWslDistribution(distribution.getId())) return null;
    return distribution;
  });

  private WSLDistribution wsl;

  @BeforeClass
  public static void checkEnvironment() {
    assumeWindows();
    assumeWslPresence();
  }

  @AfterClass
  public static void afterClass() {
    WSL = null;
  }

  @Before
  public void setUp() {
    assumeTrue("No WSL distributions available", (wsl = WSL.getValue()) != null);
  }

  @Test
  public void testWslToWinPath() {
    assertNull(wsl.getWindowsPath("/mnt/cd"));
    assertEquals("\\\\wsl$\\" + wsl.getMsId() + "\\mnt", wsl.getWindowsPath("/mnt"));
    assertEquals("\\\\wsl$\\" + wsl.getMsId(), wsl.getWindowsPath(""));
    assertNull(wsl.getWindowsPath("/mnt//test"));
    assertNull(wsl.getWindowsPath("/mnt/1/test"));

    assertEquals("C:", wsl.getWindowsPath("/mnt/c"));
    assertEquals("X:\\", wsl.getWindowsPath("/mnt/x/"));
    assertEquals("C:\\temp\\foo", wsl.getWindowsPath("/mnt/c/temp/foo"));
    assertEquals("C:\\temp\\KeepCase", wsl.getWindowsPath("/mnt/c/temp/KeepCase"));
    assertEquals("C:\\name with spaces\\another name with spaces", wsl.getWindowsPath("/mnt/c/name with spaces/another name with spaces"));
    //noinspection NonAsciiCharacters
    assertEquals("C:\\юникод", wsl.getWindowsPath("/mnt/c/юникод"));
  }

  @Test
  public void testWinToWslPath() {
    assertEquals("/mnt/c/foo", wsl.getWslPath("C:\\foo"));
    assertEquals("/mnt/c/temp/KeepCase", wsl.getWslPath("C:\\temp\\KeepCase"));
    assertNull(wsl.getWslPath("?:\\temp\\KeepCase"));
    assertNull(wsl.getWslPath("c:c"));
  }

  @Test
  public void testPaths() {
    String originalWinPath = "C:\\usr\\something\\bin\\gcc";
    assertEquals(originalWinPath, wsl.getWindowsPath(wsl.getWslPath(originalWinPath)));

    String originalWslPath = "/mnt/c/usr/bin/gcc";
    assertEquals(originalWslPath, wsl.getWslPath(wsl.getWindowsPath(originalWslPath)));
  }

  @Test
  public void testResolveSymlink() throws Exception {
    File winFile = tempDir.newFile("the_file.txt");
    File winSymlink = new File(tempDir.getRoot(), "sym_link");

    String file = wsl.getWslPath(winFile.getPath());
    String symlink = wsl.getWslPath(winSymlink.getPath());
    mkSymlink(file, symlink);

    String resolved = wsl.getWindowsPath(wsl.resolveSymlink(symlink));
    assertNotNull(resolved);
    assertTrue(new File(resolved).exists());
    assertTrue(winFile.getPath().equalsIgnoreCase(resolved));
  }

  private void mkSymlink(String file, String symlink) throws Exception {
    GeneralCommandLine cmd = wsl.patchCommandLine(new GeneralCommandLine("ln", "-s", file, symlink), null, new WSLCommandLineOptions());
    ProcessOutput output = new CapturingProcessHandler(cmd).runProcess(10_000);
    assertEquals(0, output.getExitCode());
  }
}
