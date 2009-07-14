package com.intellij.spellchecker;

import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiMethod;
import com.intellij.spellchecker.tokenizer.Token;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 *
 * @author shkate@jetbrains.com
 */
public class MethodNameTokenizerJava extends NamedElementTokenizer<PsiMethod> {

   @Override
   @Nullable
  public Token[] tokenize(@NotNull PsiMethod element) {
    final PsiMethod[] methods = (element).findDeepestSuperMethods();
    boolean isInSource = true;
    for (PsiMethod psiMethod : methods) {
      isInSource &= isMethodDeclarationInSource(psiMethod);
    }
    return isInSource ? super.tokenize(element) : null;
  }

  private static boolean isMethodDeclarationInSource(@NotNull PsiMethod psiMethod) {
    if (psiMethod.getContainingFile() == null) return false;
    final VirtualFile virtualFile = psiMethod.getContainingFile().getVirtualFile();
    if (virtualFile == null) return false;
    return ProjectRootManager.getInstance(psiMethod.getProject()).getFileIndex().isInSource(virtualFile);
  }
}
