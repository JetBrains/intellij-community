package com.intellij.openapi.fileTypes.ex;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class FileTypeChooserDialogFactoryImpl implements FileTypeChooserDialogFactory {
  @NotNull
  @Override
  public FileTypeChooserDialogImpl createFileTypeChooserDialog(@NotNull List<String> patterns, @NotNull String fileName) {
    return new FileTypeChooserDialogImpl(patterns, fileName);
  }
}
