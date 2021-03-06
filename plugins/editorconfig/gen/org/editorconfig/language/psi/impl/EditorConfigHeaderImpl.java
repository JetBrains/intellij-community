// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.editorconfig.language.psi.EditorConfigElementTypes.*;
import org.editorconfig.language.psi.base.EditorConfigHeaderBase;
import org.editorconfig.language.psi.*;

public class EditorConfigHeaderImpl extends EditorConfigHeaderBase implements EditorConfigHeader {

  public EditorConfigHeaderImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull EditorConfigVisitor visitor) {
    visitor.visitHeader(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof EditorConfigVisitor) accept((EditorConfigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<EditorConfigPattern> getPatternList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, EditorConfigPattern.class);
  }

  @Override
  @NotNull
  public List<EditorConfigPatternEnumeration> getPatternEnumerationList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, EditorConfigPatternEnumeration.class);
  }

}
