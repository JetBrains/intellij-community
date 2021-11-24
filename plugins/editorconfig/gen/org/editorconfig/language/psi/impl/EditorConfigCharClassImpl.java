// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.editorconfig.language.psi.EditorConfigElementTypes.*;
import org.editorconfig.language.psi.base.EditorConfigHeaderElementBase;
import org.editorconfig.language.psi.*;

public class EditorConfigCharClassImpl extends EditorConfigHeaderElementBase implements EditorConfigCharClass {

  public EditorConfigCharClassImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull EditorConfigVisitor visitor) {
    visitor.visitCharClass(this);
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
