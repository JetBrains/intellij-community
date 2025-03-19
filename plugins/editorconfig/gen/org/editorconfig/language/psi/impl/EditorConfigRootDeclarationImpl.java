// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import org.editorconfig.language.psi.EditorConfigRootDeclaration;
import org.editorconfig.language.psi.EditorConfigRootDeclarationKey;
import org.editorconfig.language.psi.EditorConfigRootDeclarationValue;
import org.editorconfig.language.psi.EditorConfigVisitor;
import org.editorconfig.language.psi.base.EditorConfigRootDeclarationBase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EditorConfigRootDeclarationImpl extends EditorConfigRootDeclarationBase implements EditorConfigRootDeclaration {

  public EditorConfigRootDeclarationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull EditorConfigVisitor visitor) {
    visitor.visitRootDeclaration(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof EditorConfigVisitor) accept((EditorConfigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  public @NotNull EditorConfigRootDeclarationKey getRootDeclarationKey() {
    return findNotNullChildByClass(EditorConfigRootDeclarationKey.class);
  }

  @Override
  public @NotNull List<EditorConfigRootDeclarationValue> getRootDeclarationValueList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, EditorConfigRootDeclarationValue.class);
  }

}
