// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.analysis;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RunWith(JUnit4.class)
public class JavaCoverageClassesEnumeratorTest {
  @Test
  public void collectClassFilesIncludesNestedClassesFromDirectoryRoot() throws IOException {
    Path outputRoot = Files.createTempDirectory("coverage-output");
    try {
      createClassFile(outputRoot.resolve("org/demo/Foo.class"));
      createClassFile(outputRoot.resolve("org/demo/Foo$Nested.class"));
      createClassFile(outputRoot.resolve("org/demo/Foo$Nested$Deep.class"));
      createClassFile(outputRoot.resolve("org/demo/FooKt.class"));
      createClassFile(outputRoot.resolve("org/demo/sub/Foo.class"));

      List<String> classFiles = collectClassFileNames(outputRoot, "org/demo", Set.of("Foo"));

      Assert.assertEquals(List.of("Foo$Nested$Deep.class", "Foo$Nested.class", "Foo.class"), classFiles);
    }
    finally {
      deleteRecursively(outputRoot);
    }
  }

  @Test
  public void collectClassFilesIncludesNestedClassesFromArchiveRoot() throws IOException {
    Path archiveRoot = Files.createTempFile("coverage-output", ".jar");
    try {
      try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(archiveRoot))) {
        addEntry(outputStream, "org/demo/Foo.class");
        addEntry(outputStream, "org/demo/Foo$Nested.class");
        addEntry(outputStream, "org/demo/Foo$Nested$Deep.class");
        addEntry(outputStream, "org/demo/FooKt.class");
        addEntry(outputStream, "org/demo/sub/Foo.class");
      }

      List<String> classFiles = collectClassFileNames(archiveRoot, "org/demo", Set.of("Foo"));

      Assert.assertEquals(List.of("Foo$Nested$Deep.class", "Foo$Nested.class", "Foo.class"), classFiles);
    }
    finally {
      Files.deleteIfExists(archiveRoot);
    }
  }

  private static List<String> collectClassFileNames(Path outputRoot, String packageVMName, Set<String> topLevelClassNames) {
    return JavaCoverageClassesEnumerator.collectClassFiles(outputRoot, packageVMName, topLevelClassNames).stream()
      .map(path -> path.getFileName().toString())
      .sorted()
      .toList();
  }

  private static void createClassFile(Path path) throws IOException {
    Files.createDirectories(path.getParent());
    Files.write(path, new byte[]{0});
  }

  private static void addEntry(ZipOutputStream outputStream, String name) throws IOException {
    outputStream.putNextEntry(new ZipEntry(name));
    outputStream.write(0);
    outputStream.closeEntry();
  }

  private static void deleteRecursively(Path path) throws IOException {
    if (!Files.exists(path)) return;
    try (var children = Files.walk(path)) {
      List<Path> paths = children.sorted(Comparator.reverseOrder()).toList();
      for (Path child : paths) {
        Files.deleteIfExists(child);
      }
    }
  }
}
