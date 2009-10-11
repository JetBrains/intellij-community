/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.util.io;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;

import javax.swing.filechooser.FileFilter;
import java.io.File;

public class FileTypeFilter extends FileFilter {
  private final FileType myType;

  public FileTypeFilter(FileType fileType) {
    myType = fileType;
    myDescription = myType.getDescription();
  }

  public boolean accept(File f) {
    if (f.isDirectory()) return true;
    FileType type = FileTypeManager.getInstance().getFileTypeByFileName(f.getName());
    return myType == type;
  }

  public String getDescription() {
    return myDescription;
  }

  private final String myDescription;
}
