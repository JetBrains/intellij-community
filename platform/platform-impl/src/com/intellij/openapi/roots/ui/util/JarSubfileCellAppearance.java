// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.util;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;

@ApiStatus.Internal
public final class JarSubfileCellAppearance extends ValidFileCellAppearance {
  public JarSubfileCellAppearance(VirtualFile file) {
    super(file);
  }

  @Override
  protected Icon getIcon() {
    return ArchiveFileType.INSTANCE.getIcon();
  }

  @Override
  protected int getSplitUrlIndex(String url) {
    int jarNameEnd = url.lastIndexOf(JarFileSystem.JAR_SEPARATOR.charAt(0));
    String jarUrl = jarNameEnd >= 0 ? url.substring(0, jarNameEnd) : url;
    return super.getSplitUrlIndex(jarUrl);
  }
}
