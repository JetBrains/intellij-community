// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.io.SuperUserStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static com.intellij.openapi.util.io.IoTestUtil.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Tests low-level functions for reading file case-sensitivity attributes in {@link FileSystemUtil} */
public class CaseSensitivityDetectionTest {
  @Rule public TempDirectory myTempDir = new TempDirectory();

  @Test
  public void windowsFSRootsMustHaveDefaultSensitivity() {
    assumeWindows();

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
  public void wslRootsMustBeCaseSensitive() {
    assumeWindows();

    List<@NotNull String> distributions = enumerateWslDistributions();
    assumeTrue("No WSL distributions found", !distributions.isEmpty());

    for (String name : distributions) {
      String root = "\\\\wsl$\\" + name;
      assertEquals(root, FileAttributes.CaseSensitivity.SENSITIVE, FileSystemUtil.readParentCaseSensitivity(new File(root)));
    }
  }

  @Test
  public void caseSensitivityChangesUnderWindowsMustBeReReadCorrectly() {
    assumeWindows();
    assumeWslPresence();
    assumeTrue("'fsutil.exe' needs elevated privileges to work", SuperUserStatus.isSuperUser());

    File file = myTempDir.newFile("dir/child.txt"), dir = file.getParentFile();
    assertEquals(FileAttributes.CaseSensitivity.INSENSITIVE, FileSystemUtil.readParentCaseSensitivity(file));
    setCaseSensitivity(dir, true);
    assertEquals(FileAttributes.CaseSensitivity.SENSITIVE, FileSystemUtil.readParentCaseSensitivity(file));
    setCaseSensitivity(dir, false);
    assertEquals(FileAttributes.CaseSensitivity.INSENSITIVE, FileSystemUtil.readParentCaseSensitivity(file));
  }

  @Test
  public void macOsBasics() {
    assumeMacOS();

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
