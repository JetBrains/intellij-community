// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// This is a generated file. Not intended for manual editing.
package com.intellij.openapi.vcs.changes.ignore.psi.impl;

import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;

import static com.intellij.openapi.vcs.changes.ignore.psi.IgnoreTypes.*;
import com.intellij.openapi.vcs.changes.ignore.psi.*;

public class IgnoreEntryFileImpl extends IgnoreEntryImpl implements IgnoreEntryFile {

  public IgnoreEntryFileImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof IgnoreVisitor) ((IgnoreVisitor)visitor).visitEntryFile(this);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public IgnoreNegation getNegation() {
    return findChildByClass(IgnoreNegation.class);
  }

}
