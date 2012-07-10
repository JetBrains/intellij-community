package com.jetbrains.gettext;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class GetTextFile extends PsiFileBase {
  public GetTextFile (FileViewProvider viewProvider) {
    super(viewProvider, GetTextLanguage.INSTANCE);
  }

  @NotNull
  public FileType getFileType() {
    return GetTextFileType.INSTANCE;
  }

  @Override
  public String toString() {
    return "GNU GetText File";
  }
}

