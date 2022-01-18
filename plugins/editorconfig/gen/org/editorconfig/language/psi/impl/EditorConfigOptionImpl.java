// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.editorconfig.language.psi.EditorConfigElementTypes.*;
import org.editorconfig.language.psi.base.EditorConfigOptionBase;
import org.editorconfig.language.psi.*;
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement;
import org.editorconfig.language.schema.descriptors.impl.EditorConfigOptionDescriptor;

public class EditorConfigOptionImpl extends EditorConfigOptionBase implements EditorConfigOption {

  public EditorConfigOptionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull EditorConfigVisitor visitor) {
    visitor.visitOption(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof EditorConfigVisitor) accept((EditorConfigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public EditorConfigFlatOptionKey getFlatOptionKey() {
    return findChildByClass(EditorConfigFlatOptionKey.class);
  }

  @Override
  @Nullable
  public EditorConfigOptionValueIdentifier getOptionValueIdentifier() {
    return findChildByClass(EditorConfigOptionValueIdentifier.class);
  }

  @Override
  @Nullable
  public EditorConfigOptionValueList getOptionValueList() {
    return findChildByClass(EditorConfigOptionValueList.class);
  }

  @Override
  @Nullable
  public EditorConfigOptionValuePair getOptionValuePair() {
    return findChildByClass(EditorConfigOptionValuePair.class);
  }

  @Override
  @Nullable
  public EditorConfigQualifiedOptionKey getQualifiedOptionKey() {
    return findChildByClass(EditorConfigQualifiedOptionKey.class);
  }

}
