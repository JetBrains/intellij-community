// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

// This is a generated file. Not intended for manual editing.
package com.intellij.openapi.vcs.changes.ignore.psi.impl;

import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;

import static com.intellij.openapi.vcs.changes.ignore.psi.IgnoreTypes.*;
import static com.intellij.openapi.vcs.changes.ignore.psi.IgnoreTypes.VALUE;

import com.intellij.openapi.vcs.changes.ignore.psi.IgnoreElementImpl;
import com.intellij.openapi.vcs.changes.ignore.psi.*;

public class IgnoreSyntaxImpl extends IgnoreElementImpl implements IgnoreSyntax {

  public IgnoreSyntaxImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof IgnoreVisitor) ((IgnoreVisitor)visitor).visitSyntax(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public PsiElement getValue() {
    return findNotNullChildByType(VALUE);
  }

}
