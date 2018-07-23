// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.codeInsight.actions.DirectoryFormattingOptions;
import com.intellij.codeInsight.actions.ReformatCodeAction;
import com.intellij.codeInsight.actions.TextRangeType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.SearchScope;
import com.intellij.testFramework.PlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public abstract class FileSetTestCase extends PlatformTestCase {

  protected VirtualFile createFile(@NotNull String path) {
    String[] dirNames = path.split("/");
    VirtualFile baseDir = getProject().getBaseDir();
    for (int i = 0; i < dirNames.length - 1; i ++) {
      VirtualFile existing = VfsUtilCore.findRelativeFile(dirNames[i], baseDir);
      if (existing == null) {
        baseDir = createChildDirectory(baseDir, dirNames[i]);
      }
      else {
        baseDir = existing;
      }
    }
    return createChildData(baseDir, dirNames[dirNames.length - 1]);
  }

  protected VirtualFile createFile(@NotNull String path, @NotNull String content) throws IOException {
    VirtualFile file = createFile(path);
    WriteAction.run(()->VfsUtil.saveText(file, content));
    return file;
  }

}
