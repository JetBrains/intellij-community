// This is a generated file. Not intended for manual editing.
package ru.adelf.idea.dotenv.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class DotEnvVisitor extends PsiElementVisitor {

  public void visitKey(@NotNull DotEnvKey o) {
    visitPsiElement(o);
  }

  public void visitProperty(@NotNull DotEnvProperty o) {
    visitNamedElement(o);
  }

  public void visitValue(@NotNull DotEnvValue o) {
    visitPsiElement(o);
  }

  public void visitNamedElement(@NotNull DotEnvNamedElement o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
