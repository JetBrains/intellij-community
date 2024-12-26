// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.editorconfig.language.psi.EditorConfigOptionValueIdentifier;
import org.editorconfig.language.psi.EditorConfigOptionValueList;
import org.editorconfig.language.psi.EditorConfigOptionValuePair;
import org.editorconfig.language.psi.EditorConfigVisitor;
import org.editorconfig.language.psi.base.EditorConfigDescribableElementBase;
import org.editorconfig.language.psi.interfaces.EditorConfigDescribableElement;
import org.editorconfig.language.schema.descriptors.EditorConfigDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class EditorConfigOptionValuePairImpl extends EditorConfigDescribableElementBase implements EditorConfigOptionValuePair {

  public EditorConfigOptionValuePairImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull EditorConfigVisitor visitor) {
    visitor.visitOptionValuePair(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof EditorConfigVisitor) accept((EditorConfigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  public @NotNull List<EditorConfigOptionValueIdentifier> getOptionValueIdentifierList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, EditorConfigOptionValueIdentifier.class);
  }

  @Override
  public @NotNull List<EditorConfigOptionValueList> getOptionValueListList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, EditorConfigOptionValueList.class);
  }

  @Override
  public @NotNull EditorConfigDescribableElement getFirst() {
    return EditorConfigPsiImplUtils.getFirst(this);
  }

  @Override
  public @NotNull EditorConfigDescribableElement getSecond() {
    return EditorConfigPsiImplUtils.getSecond(this);
  }

  @Override
  public @Nullable EditorConfigDescriptor getDescriptor(boolean smart) {
    return EditorConfigPsiImplUtils.getDescriptor(this, smart);
  }

}
