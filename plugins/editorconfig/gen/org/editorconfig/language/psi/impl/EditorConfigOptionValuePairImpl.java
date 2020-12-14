// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.editorconfig.language.psi.EditorConfigElementTypes.*;
import org.editorconfig.language.psi.base.EditorConfigDescribableElementBase;
import org.editorconfig.language.psi.*;
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement;
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor;

public class EditorConfigOptionValuePairImpl extends EditorConfigDescribableElementBase implements EditorConfigOptionValuePair {

  public EditorConfigOptionValuePairImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull EditorConfigVisitor visitor) {
    visitor.visitOptionValuePair(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof EditorConfigVisitor) accept((EditorConfigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<EditorConfigOptionValueIdentifier> getOptionValueIdentifierList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, EditorConfigOptionValueIdentifier.class);
  }

  @Override
  @NotNull
  public List<EditorConfigOptionValueList> getOptionValueListList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, EditorConfigOptionValueList.class);
  }

  @Override
  @NotNull
  public EditorConfigDescribableElement getFirst() {
    return EditorConfigPsiImplUtils.getFirst(this);
  }

  @Override
  @NotNull
  public EditorConfigDescribableElement getSecond() {
    return EditorConfigPsiImplUtils.getSecond(this);
  }

  @Override
  @Nullable
  public EditorConfigDescriptor getDescriptor(boolean smart) {
    return EditorConfigPsiImplUtils.getDescriptor(this, smart);
  }

}
