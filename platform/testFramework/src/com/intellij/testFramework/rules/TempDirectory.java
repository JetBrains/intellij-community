// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.rules;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.rules.ExternalResource;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An improved variant of {@link TemporaryFolder} with lazy init, no symlinks in a temporary directory path, better directory name,
 * and more convenient {@linkplain #newFile(String)} / {@linkplain #newDirectory(String)} methods.
 */
public class TempDirectory extends ExternalResource {
  private String myName;
  private final AtomicInteger myNextDirNameSuffix = new AtomicInteger();
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

  /**
   * Creates a new directory with the given relative path from the root temp directory. Throws an exception if such a directory already exists.
   */
  public @NotNull File newDirectory(@NotNull String relativePath) throws IOException {
    Path dir = Paths.get(getRoot().getPath(), relativePath);
    if (Files.exists(dir)) throw new IOException("Already exists: " + dir);
    makeDirectories(dir);
    return dir.toFile();
  }

  /**
   * Creates a new directory with random name under the root temp directory.
   */
  public @NotNull File newDirectory() throws IOException {
    return FileUtil.createTempDirectory(getRoot(), "dir" + myNextDirNameSuffix.incrementAndGet(), null);
  }

  /**
   * Creates a new file with the given relative path from the root temp directory. Throws an exception if such a file already exists.
   */
  public @NotNull File newFile(@NotNull String relativePath) throws IOException {
    return newFile(relativePath, null);
  }

  /**
   * Creates a new file with the given relative path from the root temp directory. Throws an exception if such a file already exists.
   */
  public @NotNull File newFile(@NotNull String relativePath, byte @Nullable [] content) throws IOException {
    Path file = Paths.get(getRoot().getPath(), relativePath);
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

  /**
   * @deprecated use {@link #newDirectory(String)}} instead
   */
  @Deprecated
  public @NotNull File newFolder(@NotNull String relativePath) throws IOException {
    return newDirectory(relativePath);
  }

  /**
   * @deprecated use {@link #newDirectory()} instead
   */
  @Deprecated
  public @NotNull File newFolder() throws IOException {
    return newDirectory();
  }
}