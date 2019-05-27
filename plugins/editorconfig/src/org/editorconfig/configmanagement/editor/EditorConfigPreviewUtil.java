// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.editor;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.CharUtils;
import org.editorconfig.language.psi.EditorConfigHeader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class EditorConfigPreviewUtil {


  public static final Key<String> ASSOCIATED_PREVIEW_FILE_PATH_KEY = Key.create("editorconfig.associated.preview.file");

  private EditorConfigPreviewUtil() {
  }

  @NotNull
  static List<String> extractExtensions(@NotNull EditorConfigHeader header) {
    List<String> extensions = new ArrayList<>();
    CharSequence headerChars = header.getNode().getChars();
    boolean isInExt = false;
    StringBuilder extBuilder = new StringBuilder();
    for (int i = 0; i < headerChars.length(); i ++) {
      char c = headerChars.charAt(i);
      if (c == '.') {
        isInExt = true;
      }
      else if ((CharUtils.isAsciiAlpha(c) || CharUtils.isAsciiNumeric(c)) && isInExt) {
        extBuilder.append(c);
      }
      else {
        if (isInExt && extBuilder.length() > 0) {
          extensions.add(extBuilder.toString());
          extBuilder = new StringBuilder();
        }
        isInExt = false;
      }
    }
    return extensions;
  }


  public static void associateWithPreviewFile(@NotNull VirtualFile editorConfigFile, @Nullable VirtualFile previewFile) {
    editorConfigFile.putUserData(ASSOCIATED_PREVIEW_FILE_PATH_KEY, previewFile != null ? previewFile.getPath() : null);
  }

  @Nullable
  public static VirtualFile getAssociatedPreviewFile(@NotNull VirtualFile editorConfigFile) {
    String previewPathStr = editorConfigFile.getUserData(ASSOCIATED_PREVIEW_FILE_PATH_KEY);
    if (previewPathStr != null) {
      Path previewPath = Paths.get(previewPathStr);
      return VfsUtil.findFile(previewPath, true);
    }
    return null;
  }
}
