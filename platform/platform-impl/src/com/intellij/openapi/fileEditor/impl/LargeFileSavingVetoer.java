// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentSynchronizationVetoer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import com.intellij.openapi.vfs.VirtualFileUtil;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class LargeFileSavingVetoer extends FileDocumentSynchronizationVetoer {
  @Override
  public boolean maySaveDocument(@NotNull Document document, boolean isSaveExplicit) {
    VirtualFile file = FileDocumentManager.getInstance().getFile(document);
    return file == null || !file.isValid() || !VirtualFileUtil.isTooLarge(file);
  }
}
