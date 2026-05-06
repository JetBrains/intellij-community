// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RunWith(JUnit4.class)
public class JavaCoverageEngineTest extends LightJavaCodeInsightFixtureTestCase {
  @Test
  public void getQualifiedNamesIncludesNestedClassJvmNames() {
    var file = myFixture.addFileToProject("src/org/demo/Foo.java", """
      package org.demo;

      class Foo {
        static class Nested {
          class Deep {}
        }
      }

      class Bar {}
      """);

    Assert.assertEquals(Set.of("org.demo.Foo", "org.demo.Foo$Nested", "org.demo.Foo$Nested$Deep", "org.demo.Bar"),
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

      JavaCoverageEngine engine = new JavaCoverageEngine();
      JavaCoverageSuite javaSuite = new JavaCoverageSuite(engine);
      javaSuite.setProject(getProject());
      CoverageSuitesBundle suite = new CoverageSuitesBundle(javaSuite);
      List<Integer> expectedLines = engine.collectSrcLinesForUntouchedFile(classFile, suite);
      Assert.assertNotNull(expectedLines);
      Assert.assertFalse(expectedLines.isEmpty());
      Path archiveEntry = Path.of(archiveRoot + archiveEntrySuffix);

      Assert.assertEquals(expectedLines, engine.collectSrcLinesForUntouchedFile(archiveEntry, suite));
    }
    finally {
      Files.deleteIfExists(classFile);
      Files.deleteIfExists(archiveRoot);
      Files.deleteIfExists(archiveDir);
      Files.deleteIfExists(tempDir);
    }
  }

  private static byte[] loadTestClassContent() throws IOException {
    try (var stream = Objects.requireNonNull(JavaCoverageEngineTest.class.getResourceAsStream("JavaCoverageEngineTest.class"))) {
      return stream.readAllBytes();
    }
  }
}
