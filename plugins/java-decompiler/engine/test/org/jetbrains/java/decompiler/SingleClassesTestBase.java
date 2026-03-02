// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jetbrains.java.decompiler.DecompilerTestFixture.assertFilesEqual;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class SingleClassesTestBase {
  protected DecompilerTestFixture fixture;

  protected Map<String, Object> getDecompilerOptions() {
    return Map.of();
  }

  @BeforeEach
  public void setUp() throws IOException {
    fixture = new DecompilerTestFixture();
    fixture.setUp(getDecompilerOptions());
  }

  @AfterEach
  public void tearDown() throws IOException {
    fixture.tearDown();
    fixture = null;
  }

  protected void doTest(String testFile, String... companionFiles) {
    var decompiler = fixture.getDecompiler();

    var classFile = fixture.getTestDataDir().resolve("classes/" + testFile + ".class");
    assertTrue(Files.isRegularFile(classFile));
    for (var file : collectClasses(classFile)) {
      decompiler.addSource(file.toFile());
    }

    for (String companionFile : companionFiles) {
      var companionClassFile = fixture.getTestDataDir().resolve("classes/" + companionFile + ".class");
      assertTrue(Files.isRegularFile(companionClassFile));
      for (var file : collectClasses(companionClassFile)) {
        decompiler.addSource(file.toFile());
      }
    }

    decompiler.decompileContext();

    var decompiledFile = fixture.getTargetDir().resolve(classFile.getFileName().toString().replace(".class", ".java"));
    assertTrue(Files.isRegularFile(decompiledFile));
    var referenceFile = fixture.getTestDataDir().resolve("results/" + classFile.getFileName().toString().replace(".class", ".dec"));
    assertTrue(Files.isRegularFile(referenceFile));
    assertFilesEqual(referenceFile, decompiledFile);
  }

  static List<Path> collectClasses(Path classFile) {
    var files = new ArrayList<Path>();
    files.add(classFile);

    var parent = classFile.getParent();
    if (parent != null) {
      var glob = classFile.getFileName().toString().replace(".class", "$*.class");
      try (DirectoryStream<Path> inner = Files.newDirectoryStream(parent, glob)) {
        inner.forEach(files::add);
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    return files;
  }
}
