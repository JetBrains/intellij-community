/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.fileTypes.FileTypeFactory;
import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public abstract class FileTypeManagerEx extends FileTypeManager{
  public static FileTypeManagerEx getInstanceEx(){
    return (FileTypeManagerEx)getInstance();
  }

  /**
   * @deprecated use {@link FileTypeFactory} instead
   */
  @Deprecated
  public abstract void registerFileType(@NotNull FileType fileType);
  /**
   * @deprecated use {@link FileTypeFactory} instead
   */
  @Deprecated
  public abstract void unregisterFileType(@NotNull FileType fileType);

  public abstract boolean isIgnoredFilesListEqualToCurrent(@NotNull String list);

  @NotNull
  public abstract String getExtension(@NotNull String fileName);

  public abstract void fireFileTypesChanged();

  public abstract void fireBeforeFileTypesChanged();
}
