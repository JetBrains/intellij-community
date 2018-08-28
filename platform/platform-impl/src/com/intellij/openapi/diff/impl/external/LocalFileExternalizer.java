// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
* @author Konstantin Bulenkov
*/
class LocalFileExternalizer implements ContentExternalizer {
  private final File myFile;

  public LocalFileExternalizer(File file) {
    myFile = file;
  }

  @Override
  public File getContentFile() {
    return myFile;
  }

  @Nullable
  public static LocalFileExternalizer tryCreate(VirtualFile file) {
    if (file == null || !file.isValid()) return null;
    if (!file.isInLocalFileSystem()) return null;
    return new LocalFileExternalizer(new File(file.getPath().replace('/', File.separatorChar)));
  }

  static boolean canExternalizeAsFile(VirtualFile file) {
    if (file == null || file.isDirectory()) return false;
    FileType fileType = file.getFileType();
    if (fileType.isBinary() && fileType != FileTypes.UNKNOWN) return false;
    return true;
  }
}
