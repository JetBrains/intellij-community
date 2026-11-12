// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import static java.util.Objects.requireNonNullElse;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/// Tests low-level functions for reading file case-sensitivity attributes in [FileSystemUtil].
public class CaseSensitivityDetectionTest {
  @Rule public TempDirectory tempDir = new TempDirectory();

  @Test public void windowsRoots() {
    assumeWindows();

    var systemDrive = System.getenv("SystemDrive");  // typically, "C:"
    assertNotNull(systemDrive);
    assertParentCaseSensitivity(Path.of(systemDrive + '\\'), CaseSensitivity.INSENSITIVE);

    var systemRoot = System.getenv("SystemRoot");  // typically, "C:\Windows"
    assertNotNull(systemRoot);
    assertParentCaseSensitivity(Path.of(systemRoot), CaseSensitivity.INSENSITIVE);
  }

  @Test public void wslRootsMustBeCaseSensitive() {
    var name = assumeWorkingWslDistribution();
    var root = Path.of("\\\\wsl$\\" + name);
    assertParentCaseSensitivity(root, CaseSensitivity.SENSITIVE);
  }

  @Test public void caseSensitivityChangesReloading() throws IOException {
    assumeWindows();
    assumeWslPresence();
    assumeTrue("'fsutil.exe' needs elevated privileges to work", SuperUserStatus.isSuperUser());

    var dir = tempDir.newDirectoryPath("dir");
    var file = dir.resolve("child.txt");
    assertParentCaseSensitivity(file, CaseSensitivity.INSENSITIVE);
    IoTestUtil.setCaseSensitivity(dir, true);
    assertParentCaseSensitivity(file, CaseSensitivity.SENSITIVE);
    IoTestUtil.setCaseSensitivity(dir, false);
    assertParentCaseSensitivity(file, CaseSensitivity.INSENSITIVE);
  }

  @Test public void macOsRoots() {
    assumeMacOS();
    var expected = SystemInfo.isFileSystemCaseSensitive ? CaseSensitivity.SENSITIVE : CaseSensitivity.INSENSITIVE;
    assertParentCaseSensitivity(Path.of("/"), expected);
    assertParentCaseSensitivity(Path.of("/Users"), expected);
  }

  @Test public void linuxRoots() {
    assumeLinux();
    assertParentCaseSensitivity(Path.of("/"), CaseSensitivity.SENSITIVE);
    assertParentCaseSensitivity(Path.of("/Users"), CaseSensitivity.SENSITIVE);
  }

  @Test public void caseSensitivityIsReadSanely() throws IOException {
    var file = tempDir.newFileNio("dir/x.txt");
    var sensitivity = FileSystemUtil.readParentCaseSensitivity(file);
    switch (sensitivity) {
      case SENSITIVE -> {
        Files.createFile(file.resolveSibling("X.txt"));
      }
      case INSENSITIVE -> {
        assertThatCode(() -> Files.createFile(file.resolveSibling("X.txt")))
          .doesNotThrowAnyExceptionExcept(FileAlreadyExistsException.class);
      }
      default -> fail("invalid sensitivity: " + sensitivity);
    }
  }

  @Test public void caseSensitivityOfNonExistingDirMustBeUnknown() {
    var file = tempDir.getRootPath().resolve("missing/missing.txt");
    assertFalse(Files.exists(file.getParent()));
    assertEquals(CaseSensitivity.UNKNOWN, FileSystemUtil.readDirectoryCaseSensitivityByNativeAPI(file.getParent()));
    assertEquals(CaseSensitivity.UNKNOWN, FileSystemUtil.readParentCaseSensitivityByJavaIO(file));
  }

  @Test public void nonLatinDirectory() {
    var uni = OS.CURRENT == OS.Windows ? getUnicodeName(System.getProperty("sun.jnu.encoding")) : getUnicodeName();
    assumeTrue(uni != null);
    var file = tempDir.newFileNio(uni + "/file.txt");
    var expected = SystemInfo.isFileSystemCaseSensitive ? CaseSensitivity.SENSITIVE : CaseSensitivity.INSENSITIVE;
    assertParentCaseSensitivity(file, expected);
  }

  @Test public void simpleCases() {
    var expected = SystemInfo.isFileSystemCaseSensitive ? CaseSensitivity.SENSITIVE : CaseSensitivity.INSENSITIVE;
    assertParentCaseSensitivity(tempDir.newFileNio("dir0/child.txt"), expected);
    assertParentCaseSensitivity(tempDir.newFileNio("dir0/0"), expected); // there's a toggleable "child.txt" in this dir already
    assertParentCaseSensitivity(tempDir.newDirectoryPath("dir0/Ubuntu"), expected);
  }

  private static void assertParentCaseSensitivity(Path anyChild, CaseSensitivity expected) {
    var directory = requireNonNullElse(anyChild.getParent(), anyChild);
    var actual = FileSystemUtil.readDirectoryCaseSensitivityByNativeAPI(directory);
    if (
      OS.CURRENT == OS.Windows && !OSAgnosticPathUtil.isUncPath(directory.toString()) ||
      OS.CURRENT == OS.macOS ||
      OS.CURRENT == OS.Linux && actual.isKnown()
    ) {
      assertEquals("native: " + directory, expected, actual);
    }

    actual = FileSystemUtil.readParentCaseSensitivityByJavaIO(anyChild);
    if (actual.isKnown()) {
      assertEquals("I/O: " + anyChild, expected, actual);
    }

    assertEquals(anyChild.toString(), expected, FileSystemUtil.readParentCaseSensitivity(anyChild));
  }
}
