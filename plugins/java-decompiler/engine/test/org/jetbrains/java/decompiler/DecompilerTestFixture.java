// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.decompiler.main.CancellationManager;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;

public class DecompilerTestFixture {
  private Path testDataDir;
  private Path tempDir;
  private Path targetDir;
  private TestConsoleDecompiler decompiler;

  public void setUp(Map<String, String> customOptions) throws IOException {
    setUp(customOptions, null);
  }

  public void setUp(@NotNull Map<String, String> customOptions,
                    @Nullable CancellationManager cancellationManager) throws IOException {
    testDataDir = Path.of("testData");
    if (!isTestDataDir(testDataDir)) testDataDir = Path.of("community/plugins/java-decompiler/engine/testData");
    if (!isTestDataDir(testDataDir)) testDataDir = Path.of("plugins/java-decompiler/engine/testData");
    if (!isTestDataDir(testDataDir)) testDataDir = Path.of("../community/plugins/java-decompiler/engine/testData");
    if (!isTestDataDir(testDataDir)) testDataDir = Path.of("../plugins/java-decompiler/engine/testData");
    assertTrue("cannot find the 'testData' directory relative to " + Path.of("").toAbsolutePath(), isTestDataDir(testDataDir));
    testDataDir = testDataDir.toAbsolutePath();

    tempDir = Files.createTempDirectory("decompiler_test_dir_");

    targetDir = Files.createDirectories(tempDir.resolve("decompiled"));

    Map<String, Object> options = new HashMap<>();
    options.put(IFernflowerPreferences.LOG_LEVEL, "warn");
    options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
    options.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
    options.put(IFernflowerPreferences.REMOVE_BRIDGE, "1");
    options.put(IFernflowerPreferences.LITERALS_AS_IS, "1");
    options.put(IFernflowerPreferences.UNIT_TEST_MODE, "1");
    options.putAll(customOptions);

    if (cancellationManager == null) {
      decompiler = new TestConsoleDecompiler(targetDir.toFile(), options);
    }
    else {
      decompiler = new TestConsoleDecompiler(targetDir.toFile(), options, cancellationManager);
    }
  }

  public void tearDown() throws IOException {
    try {
      if (tempDir != null) {
        deleteRecursively(tempDir);
      }
    }
    finally {
      decompiler.close();
    }
  }

  public Path getTestDataDir() {
    return testDataDir;
  }

  public Path getTempDir() {
    return tempDir;
  }

  public Path getTargetDir() {
    return targetDir;
  }

  public ConsoleDecompiler getDecompiler() {
    return decompiler;
  }

  private static boolean isTestDataDir(Path dir) {
    return Files.isDirectory(dir) && Files.isDirectory(dir.resolve("classes")) && Files.isDirectory(dir.resolve("results"));
  }

  private static void deleteRecursively(Path file) throws IOException {
    Files.walkFileTree(file, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        Files.delete(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  public static void assertFilesEqual(Path expected, Path actual) {
    if (Files.isDirectory(expected)) {
      try {
        Files.walkFileTree(expected, new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFile(Path expectedFile, BasicFileAttributes attrs) {
            Path actualFile = actual.resolve(expected.relativize(expectedFile));
            assertThat(actualFile).usingCharset(StandardCharsets.UTF_8).hasSameTextualContentAs(expectedFile, StandardCharsets.UTF_8);
            return FileVisitResult.CONTINUE;
          }
        });
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    else {
      assertThat(actual).usingCharset(StandardCharsets.UTF_8).hasSameTextualContentAs(expected, StandardCharsets.UTF_8);
    }
  }

  // cache zip files
  private static class TestConsoleDecompiler extends ConsoleDecompiler {
    private final Map<String, ZipFile> zipFiles = new HashMap<>();

    TestConsoleDecompiler(File destination, Map<String, Object> options) {
      super(destination, options, new PrintStreamLogger(System.out));
    }

    TestConsoleDecompiler(File destination, Map<String, Object> options, CancellationManager cancellationManager) {
      super(destination, options, new PrintStreamLogger(System.out), cancellationManager);
    }

    @Override
    public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
      File file = new File(externalPath);
      if (internalPath == null) {
        return InterpreterUtil.getBytes(file);
      }
      else {
        ZipFile archive = zipFiles.get(file.getName());
        if (archive == null) {
          archive = new ZipFile(file);
          zipFiles.put(file.getName(), archive);
        }
        ZipEntry entry = archive.getEntry(internalPath);
        if (entry == null) throw new IOException("Entry not found: " + internalPath);
        return InterpreterUtil.getBytes(archive, entry);
      }
    }

    void close() {
      for (ZipFile file : zipFiles.values()) {
        try {
          file.close();
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
      zipFiles.clear();
    }
  }
}
