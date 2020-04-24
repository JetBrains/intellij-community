// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.rules;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A clone of {@link TemporaryFolder} with lazy init, no symlinks in a temporary directory path, better directory name,
 * and more convenient {@linkplain #newFile(String)} / {@linkplain #newFolder(String)} methods.
 */
public class TempDirectory extends TemporaryFolder {
  private String myName;
  private File myRoot;

  @Override
  public @NotNull Statement apply(@NotNull Statement base, @NotNull Description description) {
    myName = PlatformTestUtil.lowercaseFirstLetter(FileUtil.sanitizeFileName(description.getMethodName(), false), true);
    return super.apply(base, description);
  }

  @Override
  protected void before() { }

  @Override
  protected void after() {
    if (myRoot != null) {
      Path path = myRoot.toPath();
      myRoot = null;
      myName = null;
      try {
        FileUtil.delete(path);
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }

  @Override
  public @NotNull File getRoot() {
    if (myRoot == null) {
      if (myName == null) {
        throw new IllegalStateException("apply() was not called");
      }
      try {
        myRoot = Files.createTempDirectory(UsefulTestCase.TEMP_DIR_MARKER + myName + '_').toRealPath().toFile();
      }
      catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    return myRoot;
  }

  /** Allows subdirectories in a directory name (i.e. "dir1/dir2/target"); does not fail if these intermediates already exist. */
  @Override
  public @NotNull File newFolder(@NotNull String directoryName) throws IOException {
    Path dir = Paths.get(getRoot().getPath(), directoryName);
    if (Files.exists(dir)) throw new IOException("Already exists: " + dir);
    makeDirectories(dir);
    return dir.toFile();
  }

  /** Allows subdirectories in a file name (i.e. "dir1/dir2/target"); does not fail if these intermediates already exist. */
  @Override
  public @NotNull File newFile(@NotNull String fileName) throws IOException {
    return newFile(fileName, null);
  }

  /** Allows subdirectories in a file name (i.e. "dir1/dir2/target"); does not fail if these intermediates already exist. */
  public @NotNull File newFile(@NotNull String fileName, byte @Nullable [] content) throws IOException {
    Path file = Paths.get(getRoot().getPath(), fileName);
    if (Files.exists(file)) throw new IOException("Already exists: " + file);
    makeDirectories(file.getParent());
    Files.createFile(file);
    if (content != null) {
      Files.write(file, content);
    }
    return file.toFile();
  }

  private static void makeDirectories(Path path) throws IOException {
    if (!Files.isDirectory(path)) {
      makeDirectories(path.getParent());
      Files.createDirectory(path);
    }
  }
}