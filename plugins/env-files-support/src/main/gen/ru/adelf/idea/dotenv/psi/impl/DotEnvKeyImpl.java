// This is a generated file. Not intended for manual editing.
package ru.adelf.idea.dotenv.psi.impl;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.psi.DotEnvKey;
import ru.adelf.idea.dotenv.psi.DotEnvVisitor;

public class DotEnvKeyImpl extends ASTWrapperPsiElement implements DotEnvKey {

  public DotEnvKeyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DotEnvVisitor visitor) {
    visitor.visitKey(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DotEnvVisitor) accept((DotEnvVisitor)visitor);
    else super.accept(visitor);
  }

}
