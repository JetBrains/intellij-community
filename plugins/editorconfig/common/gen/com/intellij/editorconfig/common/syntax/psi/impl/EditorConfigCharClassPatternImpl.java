// This is a generated file. Not intended for manual editing.
package com.intellij.editorconfig.common.syntax.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.editorconfig.common.syntax.psi.EditorConfigElementTypes.*;
import com.intellij.editorconfig.common.syntax.psi.*;

public class EditorConfigCharClassPatternImpl extends EditorConfigHeaderElementBase implements EditorConfigCharClassPattern {

  public EditorConfigCharClassPatternImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull EditorConfigVisitor visitor) {
    visitor.visitCharClassPattern(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof EditorConfigVisitor) accept((EditorConfigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public EditorConfigCharClassExclamation getCharClassExclamation() {
    return findChildByClass(EditorConfigCharClassExclamation.class);
  }

  @Override
  @NotNull
  public List<EditorConfigCharClassLetter> getCharClassLetterList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, EditorConfigCharClassLetter.class);
  }

}
