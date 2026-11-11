// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class JavaCoverageEngineTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void getQualifiedNamesIncludesNestedClassJvmNames() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    var file = myFixture.addFileToProject("src/org/demo/Foo.java", """
      package org.demo;

      class Foo {
        static class Nested {
          class Deep {}
        }
      }

      class Bar {}
      """);

    assertEquals(Set.of("org.demo.Foo", "org.demo.Foo$Nested", "org.demo.Foo$Nested$Deep", "org.demo.Bar"),
                 new JavaCoverageEngine().getQualifiedNames(file));
  }

  @Test
  public void collectSrcLinesForUntouchedFileReadsClassFromArchiveEntry() throws IOException {
    assertArchiveEntryReadsClass("!/com/intellij/coverage/JavaCoverageEngineTest.class");
  }

  @Test
  public void collectSrcLinesForUntouchedFileReadsClassFromWindowsArchiveEntry() throws IOException {
    assertArchiveEntryReadsClass("!\\com\\intellij\\coverage\\JavaCoverageEngineTest.class");
  }

  private void assertArchiveEntryReadsClass(String archiveEntrySuffix) throws IOException {
    byte[] content = loadTestClassContent();
    Path classFile = Files.createTempFile("coverage-output", ".class");
    Path tempDir = Files.createTempDirectory("coverage-output");
    Path archiveDir = tempDir.resolve("coverage-output!");
    Path archiveRoot = archiveDir.resolve("coverage-output.jar");
    try {
      Files.createDirectory(archiveDir);
      Files.write(classFile, content);
      try (ZipOutputStream outputStream = new ZipOutputStream(Files.newOutputStream(archiveRoot))) {
        outputStream.putNextEntry(new ZipEntry("com/intellij/coverage/JavaCoverageEngineTest.class"));
        outputStream.write(content);
        outputStream.closeEntry();
      }

      Path archiveEntry = Path.of(archiveRoot + archiveEntrySuffix);
      Path missingArchiveEntry = Path.of(archiveRoot + "!/missing.class");

      for (JavaCoverageRunner runner : List.of(new IDEACoverageRunner(), new JaCoCoCoverageRunner())) {
        JavaCoverageEngine engine = new JavaCoverageEngine();
        CoverageSuitesBundle suite = createSuite(engine, runner);
        List<Integer> expectedLines = engine.collectSrcLinesForUntouchedFile(classFile, suite);
        assertNotNull(expectedLines);
        assertFalse(expectedLines.isEmpty(), runner.getPresentableName());
        assertEquals(expectedLines, engine.collectSrcLinesForUntouchedFile(archiveEntry, suite), runner.getPresentableName());
        assertEquals(List.of(),
                     engine.collectSrcLinesForUntouchedFile(missingArchiveEntry, suite),
                     runner.getPresentableName());
      }
    }
    finally {
      Files.deleteIfExists(classFile);
      Files.deleteIfExists(archiveRoot);
      Files.deleteIfExists(archiveDir);
      Files.deleteIfExists(tempDir);
    }
  }

  private CoverageSuitesBundle createSuite(JavaCoverageEngine engine, JavaCoverageRunner runner) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    JavaCoverageSuite javaSuite = new JavaCoverageSuite(engine) {
      @Override
      public CoverageRunner getRunner() {
        return runner;
      }
    };
    javaSuite.setProject(myFixture.getProject());
    return new CoverageSuitesBundle(javaSuite);
  }

  private static byte[] loadTestClassContent() throws IOException {
    try (var stream = Objects.requireNonNull(JavaCoverageEngineTest.class.getResourceAsStream("JavaCoverageEngineTest.class"))) {
      return stream.readAllBytes();
    }
  }
}
