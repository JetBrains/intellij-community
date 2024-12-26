// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.editorconfig.language.psi.*;
import org.editorconfig.language.psi.base.EditorConfigOptionBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public @Nullable EditorConfigFlatOptionKey getFlatOptionKey() {
    return findChildByClass(EditorConfigFlatOptionKey.class);
  }

  @Override
  public @Nullable EditorConfigOptionValueIdentifier getOptionValueIdentifier() {
    return findChildByClass(EditorConfigOptionValueIdentifier.class);
  }

  @Override
  public @Nullable EditorConfigOptionValueList getOptionValueList() {
    return findChildByClass(EditorConfigOptionValueList.class);
  }

  @Override
  public @Nullable EditorConfigOptionValuePair getOptionValuePair() {
    return findChildByClass(EditorConfigOptionValuePair.class);
  }

  @Override
  public @Nullable EditorConfigQualifiedOptionKey getQualifiedOptionKey() {
    return findChildByClass(EditorConfigQualifiedOptionKey.class);
  }

}
