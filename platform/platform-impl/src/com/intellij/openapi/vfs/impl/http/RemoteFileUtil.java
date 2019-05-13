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
package com.intellij.openapi.vfs.impl.http;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class RemoteFileUtil {
  private RemoteFileUtil() {
  }

  @Nullable
  public static FileType getFileType(@Nullable String contentType) {
    if (contentType == null) return null;

    int end = contentType.indexOf(';');
    String mimeType = end == -1 ? contentType : contentType.substring(0, end);
    if (mimeType.isEmpty()) {
      return null;
    }

    for (Language language : Language.getRegisteredLanguages()) {
      String[] types = language.getMimeTypes();
      for (String type : types) {
        if (type.equalsIgnoreCase(mimeType)) {
          FileType fileType = language.getAssociatedFileType();
          if (fileType != null) {
            return fileType;
          }
        }
      }
    }
    return null;
  }

}
