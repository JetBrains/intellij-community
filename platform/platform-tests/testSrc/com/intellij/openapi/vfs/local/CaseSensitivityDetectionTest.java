// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** test low-level functions for reading file case-sensitivity attributes in {@link FileSystemUtil} */
public class CaseSensitivityDetectionTest {
  @Rule public TempDirectory myTempDir = new TempDirectory();

  @Test
  public void windowsFSRootsMustHaveDefaultSensitivity() {
    IoTestUtil.assumeWindows();

    String systemDrive = System.getenv("SystemDrive");  // typically "C:"
    assertNotNull(systemDrive);
    File root = new File(systemDrive + '\\');
    FileAttributes.CaseSensitivity rootCs = FileSystemUtil.readParentCaseSensitivity(root);
    assertEquals(systemDrive, FileAttributes.CaseSensitivity.INSENSITIVE, rootCs);

    String systemRoot = System.getenv("SystemRoot");  // typically "C:\Windows"
    assertNotNull(systemRoot);
    File child = new File(systemRoot);
    assertEquals(root, child.getParentFile());
    assertEquals(systemRoot, rootCs, FileSystemUtil.readParentCaseSensitivity(child));
  }

  @Test
  public void wslRootsMustBeCaseSensitive() throws Exception {
    IoTestUtil.assumeWindows();
    assumeTrue("'wsl.exe' not found in %Path%", PathEnvironmentVariableUtil.findInPath("wsl.exe") != null);

    ProcessOutput output = ExecUtil.execAndGetOutput(new GeneralCommandLine("wsl", "-l", "-v").withRedirectErrorStream(true), 30_000);
    List<String> lines = output.getStdoutLines().stream().map(String::trim).filter(Strings::isNotEmpty).collect(Collectors.toList());
    assumeTrue("Can't enumerate WSL distributions: " + output.getExitCode() + " stdout:\n" + output.getStdout().trim(),
               output.getExitCode() == 0 && lines.size() > 1);

    for (int i = 1; i < lines.size(); i++) {
      String name = lines.get(i).trim().split("\\s+")[1];
      File root = new File("\\\\wsl$\\" + name);
      assertEquals(FileAttributes.CaseSensitivity.SENSITIVE, FileSystemUtil.readParentCaseSensitivity(root));
    }
  }

  @Test
  public void caseSensitivityChangesUnderWindowsMustBeReReadCorrectly() throws Exception {
    IoTestUtil.assumeWindows();
    assumeTrue("'wsl.exe' not found in %Path%", PathEnvironmentVariableUtil.findInPath("wsl.exe") != null);

    File dir = myTempDir.newDirectory();
    File anyChild = new File(dir, "child.txt");
    assertTrue(anyChild.createNewFile());
    assertEquals(FileAttributes.CaseSensitivity.INSENSITIVE, FileSystemUtil.readParentCaseSensitivity(anyChild));
    WindowsCaseSensitivityTest.makeCaseSensitive(dir, true);
    assertEquals(FileAttributes.CaseSensitivity.SENSITIVE, FileSystemUtil.readParentCaseSensitivity(anyChild));
    WindowsCaseSensitivityTest.makeCaseSensitive(dir, false);
    assertEquals(FileAttributes.CaseSensitivity.INSENSITIVE, FileSystemUtil.readParentCaseSensitivity(anyChild));
  }

  @Test
  public void macOsBasics() {
    IoTestUtil.assumeMacOS();

    File root = new File("/");
    FileAttributes.CaseSensitivity rootCs = FileSystemUtil.readParentCaseSensitivity(root);
    assertNotEquals(FileAttributes.CaseSensitivity.UNKNOWN, rootCs);

    File child = new File("/Users");
    assertEquals(root, child.getParentFile());
    assertEquals(rootCs, FileSystemUtil.readParentCaseSensitivity(child));
  }

  @Test
  public void linuxBasics() {
    assumeTrue("Need Linux, can't run on " + SystemInfo.OS_NAME, SystemInfo.isLinux);

    File root = new File("/");
    FileAttributes.CaseSensitivity rootCs = FileSystemUtil.readParentCaseSensitivity(root);
    assertEquals(FileAttributes.CaseSensitivity.SENSITIVE, rootCs);

    File child = new File("/home");
    assertEquals(rootCs, FileSystemUtil.readParentCaseSensitivity(child));
  }
}
