// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.junit.jupiter.api.DynamicTest;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * File set checks for a JUnit 5 test.
 * This is the JUnit 5 replacement of {@code FileSetTestCase}.
 * <p>
 * Each test file holds input parts and the expected result.
 * The {@code ---} delimiter separates the parts. The text after the last delimiter is the expected result.
 */
@TestOnly
public final class FileSetTestUtil {
  private static final String DEFAULT_DELIMITER = "---";

  private FileSetTestUtil() {
  }

  /**
   * Transforms the input parts of a test file to the actual result.
   */
  @FunctionalInterface
  public interface Transformer {
    /**
     * @param testName the file name without the extension
     * @param data the input parts of the test file
     * @return the actual result, which the check compares with the text after the last delimiter
     */
    @NotNull String transform(@NotNull String testName, String @NotNull [] data) throws Exception;
  }

  /**
   * Returns the test files at {@code path}, as {@code FileSetTestCase} collected them.
   * {@code path} is a directory or one file. The method skips a file whose name starts with {@code _}, and {@code CVS}.
   * When the {@code fileset.pattern} system property is set, the method keeps only the files whose path matches it.
   * Call it from any thread.
   */
  public static @NotNull List<Path> collectTestFiles(@NotNull String path) throws IOException {
    Path root = Path.of(path);
    List<Path> files;
    if (Files.isDirectory(root)) {
      try (Stream<Path> children = Files.list(root)) {
        files = children.toList();
      }
    }
    else if (Files.exists(root)) {
      files = List.of(root);
    }
    else {
      throw new IllegalArgumentException("invalid path: " + path);
    }

    String pattern = System.getProperty("fileset.pattern");
    Pattern compiled = pattern != null ? Pattern.compile(pattern) : null;
    List<Path> result = new ArrayList<>();
    for (Path file : files) {
      String name = file.getFileName().toString();
      if (!Files.isRegularFile(file) || StringUtil.startsWithChar(name, '_') || "CVS".equals(name)) {
        continue;
      }
      if (compiled != null && !compiled.matcher(file.toString()).matches()) {
        continue;
      }
      result.add(file);
    }
    return result;
  }

  /**
   * Returns one dynamic test for each file of {@link #collectTestFiles(String)}.
   * The name of each test is the file name. The tests use the default charset and the {@code ---} delimiter.
   * Return the result from a {@code @TestFactory} method.
   * <p>
   * The test log names each test after the {@code @TestFactory} method.
   * {@code FileSetTestCase} named each test {@code FileSetTestCase$ActualTest}, so compare the count and the status of the tests.
   */
  public static @NotNull List<DynamicTest> dynamicTests(@NotNull String path, @NotNull Transformer transformer) throws IOException {
    return dynamicTests(path, null, DEFAULT_DELIMITER, transformer);
  }

  /**
   * Returns one dynamic test for each file of {@link #collectTestFiles(String)}.
   * The name of each test is the file name. Each test calls {@link #doTest(Path, Charset, String, Transformer)}.
   * Return the result from a {@code @TestFactory} method.
   */
  public static @NotNull List<DynamicTest> dynamicTests(@NotNull String path,
                                                        @Nullable Charset charset,
                                                        @NotNull String delimiter,
                                                        @NotNull Transformer transformer) throws IOException {
    List<DynamicTest> result = new ArrayList<>();
    for (Path file : collectTestFiles(path)) {
      result.add(DynamicTest.dynamicTest(file.getFileName().toString(), () -> doTest(file, charset, delimiter, transformer)));
    }
    return result;
  }

  /**
   * Loads {@code testFile} with {@code charset}, or with the default charset when {@code charset} is {@code null}.
   * Then splits the text at {@code delimiter}, calls {@code transformer} with the input parts, and compares the result with the expected part.
   * When the result is different, the check fails with a file comparison failure.
   * Call it from any thread. The transformer runs on the caller thread, so it must take a read action for a PSI read.
   */
  public static void doTest(@NotNull Path testFile,
                            @Nullable Charset charset,
                            @NotNull String delimiter,
                            @NotNull Transformer transformer) throws Exception {
    String content = Files.readString(testFile, charset != null ? charset : Charset.defaultCharset());
    content = StringUtil.replace(content, "\r", "");

    List<String> input = new ArrayList<>();
    int currentIndex = 0;
    int separatorIndex;
    while ((separatorIndex = content.indexOf(delimiter, currentIndex)) >= 0) {
      input.add(content.substring(currentIndex, separatorIndex));
      currentIndex = separatorIndex + delimiter.length();
      currentIndex = CharArrayUtil.shiftForward(content, currentIndex, "-\n");
    }
    if (input.isEmpty()) {
      throw new AssertionError("No data found in source file");
    }

    String testName = testFile.getFileName().toString();
    int dotIdx = testName.indexOf('.');
    if (dotIdx >= 0) {
      testName = testName.substring(0, dotIdx);
    }

    String transformed = transformer.transform(testName, ArrayUtilRt.toStringArray(input));
    String result = content.substring(0, currentIndex) + transformed;
    PlatformTestUtil.assertSameLinesWithFile(testFile.toAbsolutePath().toString(), result, true);
  }
}
