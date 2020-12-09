// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes.CaseSensitivity;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.io.SuperUserStatus;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.intellij.openapi.util.io.IoTestUtil.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Tests low-level functions for reading file case-sensitivity attributes in {@link FileSystemUtil} */
public class CaseSensitivityDetectionTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test
  public void windowsFSRootsMustHaveDefaultSensitivity() {
    assumeWindows();

    String systemDrive = System.getenv("SystemDrive");  // typically "C:"
    assertNotNull(systemDrive);
    File root = new File(systemDrive + '\\');
    CaseSensitivity rootCs = FileSystemUtil.readParentCaseSensitivity(root);
    assertEquals(systemDrive, CaseSensitivity.INSENSITIVE, rootCs);

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

    String name = distributions.get(0);
    assumeTrue("WSL distribution " + name + " doesn't seem to be alive", reanimateWslDistribution(name));

    String root = "\\\\wsl$\\" + name;
    assertEquals(root, CaseSensitivity.SENSITIVE, FileSystemUtil.readParentCaseSensitivity(new File(root)));
  }

  @Test
  public void caseSensitivityChangesUnderWindowsMustBeReReadCorrectly() throws IOException {
    assumeWindows();
    assumeWslPresence();
    assumeTrue("'fsutil.exe' needs elevated privileges to work", SuperUserStatus.isSuperUser());

    File file = tempDir.newFile("dir/child.txt"), dir = file.getParentFile();
    assertEquals(CaseSensitivity.INSENSITIVE, FileSystemUtil.readParentCaseSensitivity(file));
    setCaseSensitivity(dir, true);
    assertEquals(CaseSensitivity.SENSITIVE, FileSystemUtil.readParentCaseSensitivity(file));
    setCaseSensitivity(dir, false);
    assertEquals(CaseSensitivity.INSENSITIVE, FileSystemUtil.readParentCaseSensitivity(file));
  }

  @Test
  public void macOsBasics() {
    assumeMacOS();

    File root = new File("/");
    CaseSensitivity rootCs = FileSystemUtil.readParentCaseSensitivity(root);
    assertNotEquals(CaseSensitivity.UNKNOWN, rootCs);

    File child = new File("/Users");
    assertEquals(root, child.getParentFile());
    assertEquals(rootCs, FileSystemUtil.readParentCaseSensitivity(child));
  }

  @Test
  public void linuxBasics() {
    assumeLinux();

    File root = new File("/");
    CaseSensitivity rootCs = FileSystemUtil.readParentCaseSensitivity(root);
    assertEquals(CaseSensitivity.SENSITIVE, rootCs);

    File child = new File("/home");
    assertEquals(rootCs, FileSystemUtil.readParentCaseSensitivity(child));
  }

  @Test
  public void caseSensitivityIsReadSanely() throws IOException {
    File file = tempDir.newFile("dir/x.txt");
    CaseSensitivity sensitivity = FileSystemUtil.readParentCaseSensitivity(file);
    if (sensitivity == CaseSensitivity.SENSITIVE) {
      assertTrue(new File(file.getParentFile(), "X.txt").createNewFile());
    }
    else if (sensitivity == CaseSensitivity.INSENSITIVE) {
      assertFalse(new File(file.getParentFile(), "X.txt").createNewFile());
    }
    else {
      fail("invalid sensitivity: " + sensitivity);
    }
  }

  @Test
  public void caseSensitivityOfNonExistingDirMustBeUnknown() {
    File file = new File(tempDir.getRoot(), "dir/child.txt");
    assertFalse(file.exists());
    assertEquals(CaseSensitivity.UNKNOWN, FileSystemUtil.readParentCaseSensitivity(file));
  }

  @Test
  public void nativeApiWorksInSimpleCases() {
    File file = tempDir.newFile("dir/0");
    assertFalse(FileSystemUtil.isCaseToggleable(file.getName()));

    CaseSensitivity expected = SystemInfo.isWindows || SystemInfo.isMac ? CaseSensitivity.INSENSITIVE : CaseSensitivity.SENSITIVE;
    assertEquals(expected, FileSystemUtil.readParentCaseSensitivity(file));
  }

  @Test
  public void caseSensitivityNativeWrappersMustWorkAtLeastInSimpleCases() {
    FileAttributes.CaseSensitivity defaultCS = SystemInfo.isFileSystemCaseSensitive ? FileAttributes.CaseSensitivity.SENSITIVE : FileAttributes.CaseSensitivity.INSENSITIVE;
    assertEquals(defaultCS, FileSystemUtil.readCaseSensitivityByNativeAPI(tempDir.newFile("dir0/child.txt")));
    assertEquals(defaultCS, FileSystemUtil.readCaseSensitivityByNativeAPI(tempDir.newFile("dir0/0"))); // there's toggleable "child.txt" in this dir already
    assertEquals(defaultCS, FileSystemUtil.readCaseSensitivityByNativeAPI(tempDir.newFile("dir1/0")));
  }

  @Test
  public void caseSensitivityMustBeDeducibleByPureJavaIOAtLeastInSimpleCases() {
    FileAttributes.CaseSensitivity defaultCS = SystemInfo.isFileSystemCaseSensitive ? FileAttributes.CaseSensitivity.SENSITIVE : FileAttributes.CaseSensitivity.INSENSITIVE;
    assertEquals(defaultCS, FileSystemUtil.readParentCaseSensitivityByJavaIO(tempDir.newFile("dir0/child.txt")));
    assertEquals(defaultCS, FileSystemUtil.readParentCaseSensitivityByJavaIO(tempDir.newFile("dir0/0"))); // there's toggleable "child.txt" in this dir already
    //assertEquals(defaultCS, FileSystemUtil.readParentCaseSensitivityByJavaIO(tempDir.newFile("dir1/0")));
  }
}
