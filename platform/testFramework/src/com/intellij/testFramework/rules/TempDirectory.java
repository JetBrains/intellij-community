// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.rules;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

/**
 * A clone of {@link TemporaryFolder} with no symlinks in a temporary directory path, better directory name,
 * and more convenient {@linkplain #newFile(String)} / {@linkplain #newFolder(String)} methods.
 */
public class TempDirectory extends TemporaryFolder {
  private String myName;
  private File myRoot;

  @NotNull
  @Override
  public Statement apply(@NotNull Statement base, @NotNull Description description) {
    myName = PlatformTestUtil.lowercaseFirstLetter(FileUtil.sanitizeFileName(description.getMethodName(), false), true);
    return super.apply(base, description);
  }

  @Override
  protected void before() throws IOException {
    if (myName == null) {
      throw new IllegalStateException("apply() was not called");
    }

    @SuppressWarnings("SSBasedInspection") File dir = File.createTempFile(UsefulTestCase.TEMP_DIR_MARKER + myName + "_", "");
    assertTrue("Cannot delete: " + dir.getPath(), dir.delete() || !dir.exists());
    assertTrue("Cannot create: " + dir.getPath(), dir.mkdir() || dir.isDirectory());
    myRoot = dir.getCanonicalFile();
  }

  @Override
  protected void after() {
    if (myRoot == null) {
      throw new IllegalStateException("before() was not called");
    }

    FileUtil.delete(myRoot);
    myRoot = null;
    myName = null;
  }

  @Override
  public File getRoot() {
    if (myRoot == null) {
      throw new IllegalStateException("before() was not called");
    }

    return myRoot;
  }

  /** Allows subdirectories in a directory name (i.e. "dir1/dir2/target"); does not fail if these intermediates already exist. */
  @Override
  public File newFolder(String directoryName) throws IOException {
    Path dir = Paths.get(getRoot().getPath(), directoryName);
    if (Files.exists(dir)) throw new IOException("Already exists: " + dir);
    Files.createDirectories(dir);
    return dir.toFile();
  }

  /** Allows subdirectories in a file name (i.e. "dir1/dir2/target"); does not fail if these intermediates already exist. */
  @Override
  public File newFile(String fileName) throws IOException {
    Path file = Paths.get(getRoot().getPath(), fileName);
    if (Files.exists(file)) throw new IOException("Already exists: " + file);
    makeDirectories(file.getParent());
    Files.createFile(file);
    return file.toFile();
  }

  private static void makeDirectories(Path path) throws IOException {
    if (!Files.isDirectory(path)) {
      makeDirectories(path.getParent());
      Files.createDirectory(path);
    }
  }
}