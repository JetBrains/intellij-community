// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JavaFxFileTypeFactory {
  @NonNls public static final String FXML_EXTENSION = "fxml";
  @NonNls static final String DOT_FXML_EXTENSION = "." + FXML_EXTENSION;

  public static boolean isFxml(@NotNull PsiFile file) {
    final VirtualFile virtualFile = file.getViewProvider().getVirtualFile();
    return isFxml(virtualFile);
  }

  public static boolean isFxml(@NotNull VirtualFile virtualFile) {
    if (FXML_EXTENSION.equals(virtualFile.getExtension())) {
      final FileType fileType = virtualFile.getFileType();
      if (fileType == getFileType() && !fileType.isBinary()) {
        return virtualFile.getName().endsWith(DOT_FXML_EXTENSION);
      }
    }
    return false;
  }

  @NotNull
  public static FileType getFileType() {
    return FileTypeManager.getInstance().getFileTypeByExtension(FXML_EXTENSION);
  }
}
