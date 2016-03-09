package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;

public interface FileTypeChooserDialog {
  boolean showAndGet();

  FileType getSelectedType();

  @NotNull
  String getSelectedItem();
}
