// This is a generated file. Not intended for manual editing.
package ru.adelf.idea.dotenv.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static ru.adelf.idea.dotenv.psi.DotEnvTypes.*;
import ru.adelf.idea.dotenv.psi.DotEnvNestedVariableKeyMixin;
import ru.adelf.idea.dotenv.psi.*;
import com.intellij.openapi.util.NlsSafe;

public class DotEnvNestedVariableKeyImpl extends DotEnvNestedVariableKeyMixin implements DotEnvNestedVariableKey {

  public DotEnvNestedVariableKeyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull DotEnvVisitor visitor) {
    visitor.visitNestedVariableKey(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof DotEnvVisitor) accept((DotEnvVisitor)visitor);
    else super.accept(visitor);
  }

}
