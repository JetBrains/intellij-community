// This is a generated file. Not intended for manual editing.
package ru.adelf.idea.dotenv.psi.impl;

import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import ru.adelf.idea.dotenv.psi.DotEnvNamedElementImpl;
import ru.adelf.idea.dotenv.psi.*;

public class DotEnvPropertyImpl extends DotEnvNamedElementImpl implements DotEnvProperty {

  public DotEnvPropertyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DotEnvVisitor visitor) {
    visitor.visitProperty(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DotEnvVisitor) accept((DotEnvVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public DotEnvKey getKey() {
    return findNotNullChildByClass(DotEnvKey.class);
  }

  @Override
  @Nullable
  public DotEnvValue getValue() {
    return findChildByClass(DotEnvValue.class);
  }

  public String getKeyText() {
    return DotEnvPsiUtil.getKeyText(this);
  }

  public String getValueText() {
    return DotEnvPsiUtil.getValueText(this);
  }

  public String getName() {
    return DotEnvPsiUtil.getName(this);
  }

  public PsiElement setName(@NotNull String newName) {
    return DotEnvPsiUtil.setName(this, newName);
  }

  public PsiElement getNameIdentifier() {
    return DotEnvPsiUtil.getNameIdentifier(this);
  }

}
