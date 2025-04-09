// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package org.editorconfig.language.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.editorconfig.language.psi.EditorConfigHeader;
import org.editorconfig.language.psi.EditorConfigPattern;
import org.editorconfig.language.psi.EditorConfigVisitor;
import org.editorconfig.language.psi.base.EditorConfigHeaderBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EditorConfigHeaderImpl extends EditorConfigHeaderBase implements EditorConfigHeader {

  public EditorConfigHeaderImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(@NotNull EditorConfigVisitor visitor) {
    visitor.visitHeader(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof EditorConfigVisitor) accept((EditorConfigVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  public @Nullable EditorConfigPattern getPattern() {
    return findChildByClass(EditorConfigPattern.class);
  }

}
