// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler;

import com.intellij.rt.execution.junit.FileComparisonData;
import org.opentest4j.AssertionFailedError;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Thrown when decompiled output doesn't match the expected {@code .dec} file.
 * Implements {@link FileComparisonData} so IntelliJ can offer to automatically update the expected file.
 */
public class DecompilerFileComparisonFailedError extends AssertionFailedError implements FileComparisonData {
  private final String expected;
  private final String actual;
  private final String filePath;

  public DecompilerFileComparisonFailedError(String expected, String actual, String filePath) {
    super("Decompiled output doesn't match expected: " + filePath);
    this.expected = expected;
    this.actual = actual;
    this.filePath = filePath;
  }

  @Override public String getExpectedStringPresentation() { return expected; }
  @Override public String getActualStringPresentation() { return actual; }
  @Override public String getFilePath() { return filePath; }
  @Override public String getActualFilePath() { return null; }

  /**
   * Compares {@code actualContent} against the contents of {@code expectedFile}.
   * Line endings are normalized and trailing whitespace stripped before comparison.
   * Throws {@link DecompilerFileComparisonFailedError} if the contents differ,
   * enabling IntelliJ to offer automatic update of the expected file.
   */
  public static void assertContent(Path expectedFile, String actualContent) throws IOException {
    String expected = normalize(Files.readString(expectedFile, StandardCharsets.UTF_8));
    String actual = normalize(actualContent);
    if (!expected.equals(actual)) {
      throw new DecompilerFileComparisonFailedError(expected, actual, expectedFile.toAbsolutePath().toString());
    }
  }

  private static String normalize(String content) {
    return content.replace("\r\n", "\n").stripTrailing();
  }
}
