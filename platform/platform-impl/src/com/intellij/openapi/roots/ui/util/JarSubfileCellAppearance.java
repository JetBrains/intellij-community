// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.util;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;

public class JarSubfileCellAppearance extends ValidFileCellAppearance {
  public JarSubfileCellAppearance(VirtualFile file) {
    super(file);
  }

  @Override
  protected Icon getIcon() {
    return ((FileType)ArchiveFileType.INSTANCE).getIcon();
  }

  @Override
  protected int getSplitUrlIndex(String url) {
    int jarNameEnd = url.lastIndexOf(JarFileSystem.JAR_SEPARATOR.charAt(0));
    String jarUrl = jarNameEnd >= 0 ? url.substring(0, jarNameEnd) : url;
    return super.getSplitUrlIndex(jarUrl);
  }
}
