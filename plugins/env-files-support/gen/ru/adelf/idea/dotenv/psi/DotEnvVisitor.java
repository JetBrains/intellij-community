// This is a generated file. Not intended for manual editing.
package ru.adelf.idea.dotenv.psi;

import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiElement;

public class DotEnvVisitor extends PsiElementVisitor {

  public void visitComment(@NotNull DotEnvComment o) {
    visitPsiElement(o);
  }

  public void visitEmptyLine(@NotNull DotEnvEmptyLine o) {
    visitPsiElement(o);
  }

  public void visitProperty(@NotNull DotEnvProperty o) {
    visitPsiElement(o);
  }

  public void visitPsiElement(@NotNull PsiElement o) {
    visitElement(o);
  }

}
