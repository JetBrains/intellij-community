// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;

public final class PatchVirtualFileReader {
  private PatchVirtualFileReader() {
  }

  public static PatchReader create(final VirtualFile virtualFile) throws IOException {
    final byte[] patchContents = virtualFile.contentsToByteArray();
    final CharSequence patchText = LoadTextUtil.getTextByBinaryPresentation(patchContents, virtualFile);
    return new PatchReader(patchText);
  }
}
