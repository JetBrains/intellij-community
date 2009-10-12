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
package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.options.SchemesManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public abstract class FileTypeManagerEx extends FileTypeManager{
  public static FileTypeManagerEx getInstanceEx(){
    return (FileTypeManagerEx) getInstance();
  }

  public abstract void registerFileType(FileType fileType);
  public abstract void unregisterFileType(FileType fileType);

//  public abstract String getIgnoredFilesList();
//  public abstract void setIgnoredFilesList(String list);
  public abstract boolean isIgnoredFilesListEqualToCurrent(String list);

  @NotNull public abstract String getExtension(String fileName);

  public abstract void fireFileTypesChanged();

  public abstract void fireBeforeFileTypesChanged();

  public abstract SchemesManager<FileType, AbstractFileType> getSchemesManager();
}
