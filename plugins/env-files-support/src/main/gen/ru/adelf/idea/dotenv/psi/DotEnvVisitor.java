// This is a generated file. Not intended for manual editing.
package ru.adelf.idea.dotenv.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.model.psi.PsiExternalReferenceHost;

public class DotEnvVisitor extends PsiElementVisitor {

  public void visitKey(@NotNull DotEnvKey o) {
    visitPsiElement(o);
  }

  public void visitNestedVariableKey(@NotNull DotEnvNestedVariableKey o) {
    visitPsiExternalReferenceHost(o);
  }

  public void visitProperty(@NotNull DotEnvProperty o) {
    visitPsiElement(o);
  }

  public void visitValue(@NotNull DotEnvValue o) {
    visitPsiElement(o);
  }

  public void visitPsiExternalReferenceHost(@NotNull PsiExternalReferenceHost o) {
    visitElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
