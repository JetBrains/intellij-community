/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
