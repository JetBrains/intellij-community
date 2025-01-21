// This is a generated file. Not intended for manual editing.
package ru.adelf.idea.dotenv.psi;

import com.intellij.model.psi.PsiExternalReferenceHost;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

public class DotEnvVisitor extends PsiElementVisitor {

  public void visitKey(@NotNull DotEnvKey o) {
    visitPsiExternalReferenceHost(o);
  }

  public void visitProperty(@NotNull DotEnvProperty o) {
    visitNamedElement(o);
  }

  public void visitValue(@NotNull DotEnvValue o) {
    visitPsiElement(o);
  }

  public void visitPsiExternalReferenceHost(@NotNull PsiExternalReferenceHost o) {
    visitElement(o);
  }

  public void visitNamedElement(@NotNull DotEnvNamedElement o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
