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
import com.intellij.testFramework.UsefulTestCase;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * A clone of {@link TemporaryFolder} with no symlinks in a temporary directory path and better directory name.
 */
public class TempDirectory extends TemporaryFolder {
  private String myName = null;
  private File myRoot = null;

  @Override
  public Statement apply(Statement base, Description description) {
    myName = FileUtil.sanitizeFileName(description.getMethodName(), false);
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
}