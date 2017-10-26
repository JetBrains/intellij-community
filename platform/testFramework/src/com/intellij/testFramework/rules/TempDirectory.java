/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    File dir = new File(getRoot(), directoryName);
    if (dir.exists()) throw new IOException("Already exists: " + dir);
    if (!dir.mkdirs()) throw new IOException("Cannot create: " + dir);
    return dir;
  }

  /** Allows subdirectories in a file name (i.e. "dir1/dir2/target"); does not fail if these intermediates already exist. */
  @Override
  public File newFile(String fileName) throws IOException {
    File file = new File(getRoot(), fileName);
    if (file.exists()) throw new IOException("Already exists: " + file);
    File parent = file.getParentFile();
    if (!(parent.isDirectory() || parent.mkdirs()) || !file.createNewFile()) throw new IOException("Cannot create: " + file);
    return file;
  }
}