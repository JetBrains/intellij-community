// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.refactoring.move;

import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Copies the directory `before` of the test to the project, runs {@link #perform}, and compares the result with the directory `after`.
 * It replaces the JUnit 3 base `LightMultiFileTestCase`.
 */
@TestApplication
public abstract class GroovyMoveTestBase {
  protected final TestFixture<LightJavaCodeInsightTestFixture> groovy;

  protected GroovyMoveTestBase(@NotNull TestFixture<LightJavaProject> project, @NotNull String testDataPath) {
    groovy = groovyFixture(project, testDataPath);
  }

  protected void doTest(String newPackageName, String... names) {
    String testName = groovy.get().getTestName(true);
    VirtualFile actualDirectory = groovy.get().copyDirectoryToProject(testName + "/before", "");
    String expectedPath = groovy.get().getTestDataPath().replace(File.separatorChar, '/') + testName + "/after";
    VirtualFile rootAfter = StandardFileSystems.local().findFileByPath(expectedPath);
    assertNotNull(rootAfter);
    // The refactoring and the comparison, which saves the documents, run on the EDT.
    EdtTestUtil.runInEdtAndWait(() -> {
      perform(newPackageName, names);
      VfsUtilCore.visitChildrenRecursively(actualDirectory, new VirtualFileVisitor<Void>() {
        @Override
        public boolean visitFile(@NotNull VirtualFile file) {
          file.putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, Path.of(expectedPath, VfsUtil.getRelativePath(file, actualDirectory)).toString());
          return super.visitFile(file);
        }
      });
      try {
        PlatformTestUtil.assertDirectoriesEqual(rootAfter, actualDirectory);
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    });
  }

  abstract void perform(String newPackageName, String[] names);
}
