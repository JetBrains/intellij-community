// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

// This is a generated file. Not intended for manual editing.
package com.intellij.devkit.apiDump.lang.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.devkit.apiDump.lang.psi.ADElementTypes.*;
import com.intellij.devkit.apiDump.lang.psi.*;
import com.intellij.psi.tree.IElementType;

public class ADConstructorImplGen extends ADMemberImplGen implements ADConstructor {

  public ADConstructorImplGen(@NotNull IElementType type) {
    super(type);
  }

  @Override
  public void accept(@NotNull ADVisitor visitor) {
    visitor.visitConstructor(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ADVisitor) accept((ADVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public ADConstructorReference getConstructorReference() {
    return PsiTreeUtil.getChildOfType(this, ADConstructorReference.class);
  }

  @Override
  @Nullable
  public ADModifiers getModifiers() {
    return PsiTreeUtil.getChildOfType(this, ADModifiers.class);
  }

  @Override
  @NotNull
  public ADParameters getParameters() {
    return PsiTreeUtil.getChildOfType(this, ADParameters.class);
  }

  @Override
  @Nullable
  public ADTypeReference getTypeReference() {
    return PsiTreeUtil.getChildOfType(this, ADTypeReference.class);
  }

  @Override
  @Nullable
  public PsiElement getColon() {
    return findPsiChildByType(COLON);
  }

  @Override
  @NotNull
  public PsiElement getMinus() {
    return findPsiChildByType(MINUS);
  }

  @Override
  public @NotNull PsiElement getNavigationElement() {
    return ADPsiImplUtil.getNavigationElement(this);
  }

}
