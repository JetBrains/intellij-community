package com.jetbrains.json;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public class JsonFile extends PsiFileBase {
  @NotNull private final FileType myType;

  public JsonFile(FileViewProvider fileViewProvider, @NotNull FileType type) {
    super(fileViewProvider, JsonLanguage.INSTANCE);
    myType = type;
  }

  @NotNull
  @Override
  public FileType getFileType() {
    return myType;
  }
}
