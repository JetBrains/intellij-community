// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.FileInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecompilerAssertionsTest {
  @Test
  void mismatchReportsExpectedFile(@TempDir Path dir) throws IOException {
    Path expectedFile = dir.resolve("Sample.dec");
    Files.writeString(expectedFile, "class A {}\r\n\n");

    AssertionFailedError error = assertThrows(
      AssertionFailedError.class, () -> DecompilerAssertions.assertContent(expectedFile, "class B {}"));

    assertTrue(error.isExpectedDefined());
    FileInfo expected = assertInstanceOf(FileInfo.class, error.getExpected().getValue());
    assertEquals(expectedFile.toAbsolutePath().toString(), expected.getPath());
    assertEquals("class A {}", expected.getContentsAsString(StandardCharsets.UTF_8));
    assertTrue(error.isActualDefined());
    assertEquals("class B {}", error.getActual().getValue());
  }

  @Test
  void matchIgnoresLineEndingsAndTrailingWhitespace(@TempDir Path dir) throws IOException {
    Path expectedFile = dir.resolve("Sample.dec");
    Files.writeString(expectedFile, "class A {\r\n}\r\n");

    assertDoesNotThrow(() -> DecompilerAssertions.assertContent(expectedFile, "class A {\n}"));
  }
}