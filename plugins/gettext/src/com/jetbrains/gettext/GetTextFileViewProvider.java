package com.jetbrains.gettext;

import com.intellij.lang.Language;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import org.jetbrains.annotations.NotNull;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class GetTextFileViewProvider  extends SingleRootFileViewProvider {

  protected GetTextFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile virtualFile, final boolean physical, @NotNull Language language) {
    super(manager, virtualFile, physical, language);
  }

  public boolean supportsIncrementalReparse(@NotNull final Language rootLanguage) {
    return false;
  }
}
