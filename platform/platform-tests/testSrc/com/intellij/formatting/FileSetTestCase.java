// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class FileSetTestCase extends HeavyPlatformTestCase {
  protected VirtualFile createFile(@NotNull String path) {
    String[] dirNames = path.split("/");
    VirtualFile baseDir = PlatformTestUtil.getOrCreateProjectBaseDir(getProject());
    for (int i = 0; i < dirNames.length - 1; i++) {
      VirtualFile existing = VfsUtilCore.findRelativeFile(dirNames[i], baseDir);
      if (existing == null) {
        baseDir = createChildDirectory(baseDir, dirNames[i]);
      }
      else {
        baseDir = existing;
      }
    }
    String last = dirNames[dirNames.length - 1];
    return path.endsWith("/") ? createChildDirectory(baseDir, last) : createChildData(baseDir, last);
  }

  protected VirtualFile createFile(@NotNull String path, @NotNull String content) throws IOException {
    VirtualFile file = createFile(path);
    WriteAction.run(() -> VfsUtil.saveText(file, content));
    return file;
  }
}
