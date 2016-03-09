package com.intellij.openapi.fileTypes.ex;

import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface FileTypeChooserDialogFactory {
  @NotNull
  FileTypeChooserDialog createFileTypeChooserDialog(@NotNull List<String> patterns, @NotNull String fileName);

  class SERVICE {
    public static FileTypeChooserDialogFactory getInstance() {
      return ServiceManager.getService(FileTypeChooserDialogFactory.class);
    }
  }
}
