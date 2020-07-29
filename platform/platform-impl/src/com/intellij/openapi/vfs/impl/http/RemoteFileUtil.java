// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.http;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.Nullable;

public final class RemoteFileUtil {
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
