// This is a generated file. Not intended for manual editing.
package ru.adelf.idea.dotenv.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static ru.adelf.idea.dotenv.psi.DotEnvTypes.*;
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

  @Override
  public String getKeyText() {
    return DotEnvPsiUtil.getKeyText(this);
  }

  @Override
  public String getValueText() {
    return DotEnvPsiUtil.getValueText(this);
  }

  @Override
  public String getName() {
    return DotEnvPsiUtil.getName(this);
  }

  @Override
  public PsiElement setName(@NotNull String newName) {
    return DotEnvPsiUtil.setName(this, newName);
  }

  @Override
  public PsiElement getNameIdentifier() {
    return DotEnvPsiUtil.getNameIdentifier(this);
  }

}
