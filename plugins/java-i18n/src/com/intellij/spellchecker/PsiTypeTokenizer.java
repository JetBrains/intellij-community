package com.intellij.spellchecker;

import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.util.PsiUtil;
import com.intellij.spellchecker.tokenizer.Token;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class PsiTypeTokenizer extends Tokenizer<PsiTypeElement> {

  @Nullable
  @Override
  public Token[] tokenize(@NotNull PsiTypeElement element) {
    final PsiClass psiClass = PsiUtil.resolveClassInType(element.getType());

    if (psiClass == null || psiClass.getContainingFile() == null || psiClass.getContainingFile().getVirtualFile() == null) {
      return null;
    }

    final VirtualFile virtualFile = psiClass.getContainingFile().getVirtualFile();

    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(element.getProject()).getFileIndex();

    final boolean isInSource = (virtualFile != null) && fileIndex.isInContent(virtualFile);

    return !isInSource ? null : new Token[]{new Token<PsiTypeElement>(element, element.getText(), true)};
  }
}