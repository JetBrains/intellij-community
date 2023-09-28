// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageSchemaDescriptor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class JavaFxFileTypeFactory implements FileTypeUsageSchemaDescriptor {
  @Override
  public boolean describes(@NotNull Project project, @NotNull VirtualFile file) {
    return isFxml(file);
  }

  public static final @NonNls String FXML_EXTENSION = "fxml";
  static final @NonNls String DOT_FXML_EXTENSION = "." + FXML_EXTENSION;

  public static boolean isFxml(@NotNull PsiFile file) {
    final VirtualFile virtualFile = file.getViewProvider().getVirtualFile();
    return isFxml(virtualFile);
  }

  public static boolean isFxml(@NotNull VirtualFile virtualFile) {
    if (virtualFile.getName().endsWith(DOT_FXML_EXTENSION)) {
      FileType fileType = virtualFile.getFileType();
      return fileType == getFileType() && !fileType.isBinary();
    }
    return false;
  }

  public static @NotNull FileType getFileType() {
    return FileTypeManager.getInstance().getFileTypeByExtension(FXML_EXTENSION);
  }
}
