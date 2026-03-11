// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler.roundTrip.fixtures;

import org.jetbrains.java.decompiler.DecompilerTestDataUtil;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * An abstract base class for conducting round-trip tests of bytecode decompilation using a specified compiler.
 * This class supports compiling and decompiling source code while verifying that the decompiled output matches
 * the expected results stored in test data files.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS) // timeout in case decompiler hangs
public abstract class DecompilerRoundTripTestCase {
  private static final String SRC_DIR = "src";
  private static final String RESULTS_DIR = "results";

  /**
   * Executes a round-trip test by compiling and decompiling the given source file, and comparing
   * the decompiled output with the expected result stored on disk.
   *
   * @param compiler the compiler to be used for compiling the source file
   * @param sourceFile the name of the primary source file to be tested
   * @param companionFileSystemItems additional filesystem items (e.g., directories or files) that
   *                                 contain companion source files required for compilation
   */
  protected void doTest(Compiler compiler, String sourceFile, List<String> compileOptions, String... companionFileSystemItems) {
    Path testDataDir = DecompilerTestDataUtil.findTestDataDir().resolve("roundTrip");
    Path baseDir = testDataDir.resolve(testCaseDir());
    Path resultsDir = baseDir.resolve(RESULTS_DIR);

    Path expectedFile = resultsDir.resolve(sourceFile + "." + compiler.getId() + ".dec");
    if (!Files.exists(expectedFile)) {
      expectedFile = resultsDir.resolve(sourceFile + ".dec");
    }

    String actual = compileDecompile(baseDir, compiler, sourceFile, compileOptions, companionFileSystemItems);
    try {
      if (Files.exists(expectedFile)) {
        String expectedContent = Files.readString(expectedFile);
        Assertions.assertEquals(expectedContent, actual);
      } else {
        Files.createDirectories(resultsDir);
        Files.writeString(resultsDir.resolve(sourceFile + "." + compiler.getId() + ".dec"), actual, StandardOpenOption.CREATE_NEW);
        Assertions.fail("Expected file not found: " + expectedFile);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String compileDecompile(
    Path baseDir,
    Compiler compiler,
    String sourceFile,
    List<String> compileOptions,
    String... companionFileSystemItems
  ) {
    Path srcDir = baseDir.resolve(SRC_DIR);

    Set<Path> allSources = new HashSet<>();
    allSources.add(srcDir.resolve(sourceFile + compiler.getSourceExtension()));
    for (String companionFileSystemItem : companionFileSystemItems) {
      try {
        Files.walk(baseDir.resolve(companionFileSystemItem)).forEach((item) -> {
          if (item.getFileName().toString().endsWith(compiler.getSourceExtension())) {
            allSources.add(item);
          }
        });
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    Map<String, byte[]> classBytes = compiler.compile(allSources, compileOptions);
    return RoundTripTestUtil.decompileInMemory(getDecompilerOptions(), classBytes, sourceFile);
  }

  /**
   * @return directory name for the test case.
   */
  protected abstract String testCaseDir();

  protected Map<String, Object> getDecompilerOptions() {
    Map<String, Object> options = new HashMap<>();
    options.put(IFernflowerPreferences.LOG_LEVEL, "warn");
    options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
    options.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
    options.put(IFernflowerPreferences.REMOVE_BRIDGE, "1");
    options.put(IFernflowerPreferences.LITERALS_AS_IS, "1");
    options.put(IFernflowerPreferences.UNIT_TEST_MODE, "1");
    options.put(IFernflowerPreferences.NEW_LINE_SEPARATOR, "1");
    options.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1");
    options.put(IFernflowerPreferences.DUMP_ORIGINAL_LINES, "1");
    options.put(IFernflowerPreferences.IGNORE_INVALID_BYTECODE, "1");
    options.put(IFernflowerPreferences.VERIFY_ANONYMOUS_CLASSES, "1");
    options.put(IFernflowerPreferences.CONVERT_PATTERN_SWITCH, "1");
    options.put(IFernflowerPreferences.CONVERT_RECORD_PATTERN, "1");
    options.put(IFernflowerPreferences.INLINE_SIMPLE_LAMBDAS, "1");
    options.put(IFernflowerPreferences.CHECK_CLOSABLE_INTERFACE, "0");
    options.put(IFernflowerPreferences.HIDE_RECORD_CONSTRUCTOR_AND_GETTERS, "0");
    options.put(IFernflowerPreferences.MAX_DIRECT_NODES_COUNT, 20000);
    options.put(IFernflowerPreferences.MAX_DIRECT_VARIABLE_NODE_COUNT, 30000);
    options.put(IFernflowerPreferences.PARENTHESES_FOR_BITWISE_OPERATIONS, "1");
    return options;
  }
}
