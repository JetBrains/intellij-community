// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileAttributes.CaseSensitivity;
import com.intellij.testFramework.rules.TempDirectory;
import com.intellij.util.io.SuperUserStatus;
import com.intellij.util.system.OS;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.openapi.util.io.IoTestUtil.assumeLinux;
import static com.intellij.openapi.util.io.IoTestUtil.assumeMacOS;
import static com.intellij.openapi.util.io.IoTestUtil.assumeWindows;
import static com.intellij.openapi.util.io.IoTestUtil.assumeWorkingWslDistribution;
import static com.intellij.openapi.util.io.IoTestUtil.assumeWslPresence;
import static com.intellij.openapi.util.io.IoTestUtil.getUnicodeName;
import static com.intellij.openapi.util.io.IoTestUtil.setCaseSensitivity;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/** Tests low-level functions for reading file case-sensitivity attributes in {@link FileSystemUtil} */
@SuppressWarnings("IO_FILE_USAGE")
public class CaseSensitivityDetectionTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test public void windowsFSRootsMustHaveDefaultSensitivity() {
    assumeWindows();

    var systemDrive = System.getenv("SystemDrive");  // typically, "C:"
    assertNotNull(systemDrive);
    var root = Path.of(systemDrive + '\\');
    var rootCs = FileSystemUtil.readParentCaseSensitivity(root);
    assertEquals(systemDrive, CaseSensitivity.INSENSITIVE, rootCs);

    var systemRoot = System.getenv("SystemRoot");  // typically, "C:\Windows"
    assertNotNull(systemRoot);
    var child = Path.of(systemRoot);
    assertEquals(root, child.getParent());
    assertEquals(systemRoot, rootCs, FileSystemUtil.readParentCaseSensitivity(child));
  }

  @Test public void wslRootsMustBeCaseSensitive() {
    var name = assumeWorkingWslDistribution();
    var root = Path.of("\\\\wsl$\\" + name);
    assertEquals(root.toString(), CaseSensitivity.SENSITIVE, FileSystemUtil.readParentCaseSensitivity(root));
  }

  @Test public void caseSensitivityChangesUnderWindowsMustBeReReadCorrectly() throws IOException {
    assumeWindows();
    assumeWslPresence();
    assumeTrue("'fsutil.exe' needs elevated privileges to work", SuperUserStatus.isSuperUser());

    var dir = tempDir.newDirectoryPath("dir");
    var file = dir.resolve("child.txt");
    assertEquals(CaseSensitivity.INSENSITIVE, FileSystemUtil.readParentCaseSensitivity(file));
    setCaseSensitivity(dir, true);
    assertEquals(CaseSensitivity.SENSITIVE, FileSystemUtil.readParentCaseSensitivity(file));
    setCaseSensitivity(dir, false);
    assertEquals(CaseSensitivity.INSENSITIVE, FileSystemUtil.readParentCaseSensitivity(file));
  }

  @Test public void macOsBasics() {
    assumeMacOS();

    var root = Path.of("/");
    var rootCs = FileSystemUtil.readParentCaseSensitivity(root);
    assertNotEquals(CaseSensitivity.UNKNOWN, rootCs);

    var child = Path.of("/Users");
    assertEquals(root, child.getParent());
    assertEquals(rootCs, FileSystemUtil.readParentCaseSensitivity(child));
  }

  @Test public void linuxBasics() {
    assumeLinux();

    var root = Path.of("/");
    var rootCs = FileSystemUtil.readParentCaseSensitivity(root);
    assertEquals(CaseSensitivity.SENSITIVE, rootCs);

    var child = Path.of("/home");
    assertEquals(rootCs, FileSystemUtil.readParentCaseSensitivity(child));
  }

  @Test public void caseSensitivityIsReadSanely() throws IOException {
    var file = tempDir.newFileNio("dir/x.txt");
    var sensitivity = FileSystemUtil.readParentCaseSensitivity(file);
    if (sensitivity == CaseSensitivity.SENSITIVE) {
      Files.createFile(file.resolveSibling("X.txt"));
    }
    else if (sensitivity == CaseSensitivity.INSENSITIVE) {
      assertThatCode(() -> Files.createFile(file.resolveSibling("X.txt")))
        .doesNotThrowAnyExceptionExcept(FileAlreadyExistsException.class);
    }
    else {
      fail("invalid sensitivity: " + sensitivity);
    }
  }

  @Test public void caseSensitivityOfNonExistingDirMustBeUnknown() {
    var file = tempDir.getRootPath().resolve("dir/child.txt");
    assertFalse(Files.exists(file.getParent()));
    assertEquals(CaseSensitivity.UNKNOWN, FileSystemUtil.readParentCaseSensitivityByNativeAPI(file));
    assertEquals(CaseSensitivity.UNKNOWN, FileSystemUtil.readParentCaseSensitivityByJavaNio(file));
  }

  @Test public void nativeApiWorksInSimpleCases() {
    var file = tempDir.newFileNio("dir/0");
    assertFalse(FileSystemUtil.isCaseToggleable(file.getFileName().toString()));

    var expected = OS.CURRENT == OS.Windows || OS.CURRENT == OS.macOS ? CaseSensitivity.INSENSITIVE : CaseSensitivity.SENSITIVE;
    assertEquals(expected, FileSystemUtil.readParentCaseSensitivity(file));
  }

  @Test public void nativeApiWorksWithNonLatinPaths() {
    var uni = OS.CURRENT == OS.Windows ? getUnicodeName(System.getProperty("sun.jnu.encoding")) : getUnicodeName();
    assumeTrue(uni != null);
    var file = tempDir.newFileNio(uni + "/0");
    var expected = OS.CURRENT == OS.Windows || OS.CURRENT == OS.macOS ? CaseSensitivity.INSENSITIVE : CaseSensitivity.SENSITIVE;
    assertEquals(expected, FileSystemUtil.readParentCaseSensitivity(file));
  }

  @Test public void caseSensitivityNativeWrappersMustWorkAtLeastInSimpleCases() {
    var defaultCS = SystemInfo.isFileSystemCaseSensitive ? CaseSensitivity.SENSITIVE : CaseSensitivity.INSENSITIVE;
    assertEquals(defaultCS, FileSystemUtil.readParentCaseSensitivityByNativeAPI(tempDir.newFileNio("dir0/child.txt")));
    assertEquals(defaultCS, FileSystemUtil.readParentCaseSensitivityByNativeAPI(tempDir.newFileNio("dir0/0"))); // there's a toggleable "child.txt" in this dir already
    assertEquals(defaultCS, FileSystemUtil.readParentCaseSensitivityByNativeAPI(tempDir.newFileNio("dir1/0")));
  }

  @Test public void caseSensitivityMustBeDeducibleByPureJavaIOAtLeastInSimpleCases() {
    var defaultCS = SystemInfo.isFileSystemCaseSensitive ? CaseSensitivity.SENSITIVE : CaseSensitivity.INSENSITIVE;
    assertEquals(defaultCS, FileSystemUtil.readParentCaseSensitivityByJavaNio(tempDir.newFileNio("dir0/child.txt")));
    assertEquals(defaultCS, FileSystemUtil.readParentCaseSensitivityByJavaNio(tempDir.newFileNio("dir0/0"))); // there's a toggleable "child.txt" in this dir already
    assertEquals(defaultCS, FileSystemUtil.readParentCaseSensitivityByJavaNio(tempDir.newDirectoryPath("dir0/Ubuntu")));
  }
}
