/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.testAssistant.vfs;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.dummy.DummyCachingFileSystem;
import org.jetbrains.annotations.NotNull;

public class TestDataGroupFileSystem extends DummyCachingFileSystem<VirtualFile> {
  /**
   * We must have a separator for two arbitrary file paths, considering that almost all symbols are possible in Unix paths.
   * It is very unlikely that this UUID will be present in file path so it's a pretty reliable separator.
   */
  private static final String GROUP_FILES_SEPARATOR = "33d0ee30-8c8f-11e7-bb31-be2e44b06b34";
  private static final String PROTOCOL = "testdata";

  public TestDataGroupFileSystem() {
    super(PROTOCOL);
  }


  public static TestDataGroupFileSystem getTestDataGroupFileSystem() {
    return (TestDataGroupFileSystem)VirtualFileManager.getInstance().getFileSystem(PROTOCOL);
  }

  public static String getPath(VirtualFile beforeFile, VirtualFile afterFile) {
    return beforeFile.getPath() + GROUP_FILES_SEPARATOR + afterFile.getPath();
  }


  @Override
  protected VirtualFile findFileByPathInner(@NotNull String path) {
    String[] parts = path.split(GROUP_FILES_SEPARATOR);
    if (parts.length != 2) {
      return null;
    }

    String beforePath = parts[0];
    String afterPath = parts[1];
    if (StringUtil.isEmpty(beforePath) || StringUtil.isEmpty(afterPath)) {
      return null;
    }

    LocalFileSystem localFileSystem = LocalFileSystem.getInstance();
    VirtualFile beforeFile = localFileSystem.refreshAndFindFileByPath(beforePath);
    VirtualFile afterFile = localFileSystem.refreshAndFindFileByPath(afterPath);
    if (beforeFile == null || afterFile == null) {
      return null;
    }

    return new TestDataGroupVirtualFile(beforeFile, afterFile);
  }
}
