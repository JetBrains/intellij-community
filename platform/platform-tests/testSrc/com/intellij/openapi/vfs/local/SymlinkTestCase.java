/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.local;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.testFramework.LightPlatformLangTestCase;

import java.io.File;

import static com.intellij.openapi.util.io.IoTestUtil.createTestDir;

public abstract class SymlinkTestCase extends LightPlatformLangTestCase {
  protected LocalFileSystem myFileSystem;
  protected File myTempDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFileSystem = LocalFileSystem.getInstance();
    myTempDir = createTestDir("temp");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      super.tearDown();
    }
    finally {
      IoTestUtil.delete(myTempDir);
    }
  }

  @Override
  protected void runTest() throws Throwable {
    if (SystemInfo.areSymLinksSupported) {
      super.runTest();
    }
    else {
      System.err.println("Skipped: " + getName());
    }
  }

  protected void refresh() {
    assertTrue(myTempDir.getPath(), myTempDir.isDirectory() || myTempDir.mkdirs());

    VirtualFile tempDir = myFileSystem.refreshAndFindFileByIoFile(myTempDir);
    assertNotNull(myTempDir.getPath(), tempDir);

    tempDir.getChildren();
    tempDir.refresh(false, true);
    VfsUtilCore.visitChildrenRecursively(tempDir, new VirtualFileVisitor() { });
  }
}
