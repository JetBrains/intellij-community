// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler;

import org.opentest4j.AssertionFailedError;
import org.opentest4j.FileInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DecompilerAssertions {
  private DecompilerAssertions() {
  }

  /**
   * Compares {@code actualContent} with the contents of {@code expectedFile}.
   * Line endings are normalized and trailing whitespace is stripped before the comparison.
   * The failure has a {@link FileInfo} as the expected value, so the IDE can update the expected file.
   */
  public static void assertContent(Path expectedFile, String actualContent) throws IOException {
    String expected = normalize(Files.readString(expectedFile, StandardCharsets.UTF_8));
    String actual = normalize(actualContent);
    if (!expected.equals(actual)) {
      String path = expectedFile.toAbsolutePath().toString();
      throw new AssertionFailedError("Decompiled output doesn't match expected: " + path,
                                     new FileInfo(path, expected.getBytes(StandardCharsets.UTF_8)), actual);
    }
  }

  private static String normalize(String content) {
    return content.replace("\r\n", "\n").stripTrailing();
  }
}