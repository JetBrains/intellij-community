// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.wsl;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static com.intellij.openapi.util.io.IoTestUtil.assumeWindows;
import static com.intellij.openapi.util.io.IoTestUtil.assumeWslPresence;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class WSLUtilTest extends BareTestFixtureTestCase {
  @Rule public TempDirectory tempDirectory = new TempDirectory();

  private WSLDistribution myWSL;

  @BeforeClass
  public static void checkEnvironment() {
    assumeWindows();
    assumeWslPresence();
  }

  @Before
  public void setUp() {
    List<WSLDistribution> distributions = WSLUtil.getAvailableDistributions();
    assumeTrue("No WSL distributions available", distributions.size() > 0);
    myWSL = distributions.get(0);
  }

  @Test
  public void testWslToWinPath() {
    assertNull(myWSL.getWindowsPath("/mnt/cd"));
    assertNull(myWSL.getWindowsPath("/mnt"));
    assertNull(myWSL.getWindowsPath(""));
    assertNull(myWSL.getWindowsPath("/mnt//test"));
    assertNull(myWSL.getWindowsPath("/mnt/1/test"));

    assertEquals("C:", myWSL.getWindowsPath("/mnt/c"));
    assertEquals("X:\\", myWSL.getWindowsPath("/mnt/x/"));
    assertEquals("C:\\temp\\foo", myWSL.getWindowsPath("/mnt/c/temp/foo"));
    assertEquals("C:\\temp\\KeepCase", myWSL.getWindowsPath("/mnt/c/temp/KeepCase"));
    assertEquals("C:\\name with spaces\\another name with spaces", myWSL.getWindowsPath("/mnt/c/name with spaces/another name with spaces"));
    //noinspection NonAsciiCharacters
    assertEquals("C:\\юникод", myWSL.getWindowsPath("/mnt/c/юникод"));
  }

  @Test
  public void testWinToWslPath() {
    assertEquals("/mnt/c/foo", myWSL.getWslPath("C:\\foo"));
    assertEquals("/mnt/c/temp/KeepCase", myWSL.getWslPath("C:\\temp\\KeepCase"));
    assertNull(myWSL.getWslPath("?:\\temp\\KeepCase"));
    assertNull(myWSL.getWslPath("c:c"));
  }

  @Test
  public void testPaths() {
    String originalWinPath = "C:\\usr\\something\\bin\\gcc";
    assertEquals(originalWinPath, myWSL.getWindowsPath(myWSL.getWslPath(originalWinPath)));

    String originalWslPath = "/mnt/c/usr/bin/gcc";
    assertEquals(originalWslPath, myWSL.getWslPath(myWSL.getWindowsPath(originalWslPath)));
  }

  @Test
  public void testResolveSymlink() throws Exception {
    File winFile = tempDirectory.newFile("the_file.txt");
    File winSymlink = new File(tempDirectory.getRoot(), "sym_link");

    String file = myWSL.getWslPath(winFile.getPath());
    String symlink = myWSL.getWslPath(winSymlink.getPath());
    mkSymlink(file, symlink);

    String resolved = myWSL.getWindowsPath(myWSL.resolveSymlink(symlink));
    assertNotNull(resolved);
    assertTrue(new File(resolved).exists());
    assertTrue(winFile.getPath().equalsIgnoreCase(resolved));
  }

  private void mkSymlink(String file, String symlink) throws Exception {
    GeneralCommandLine cmd = myWSL.patchCommandLine(new GeneralCommandLine("ln", "-s", file, symlink), null, new WSLCommandLineOptions());
    ProcessOutput output = WSLUtil.addInputCloseListener(new CapturingProcessHandler(cmd)).runProcess(10_000);
    assertFalse(output.isTimeout());
    assertEquals(0, output.getExitCode());
  }
}
