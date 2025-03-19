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
import com.intellij.psi.PsiClass;
import com.intellij.psi.tree.IElementType;

public class ADClassDeclarationImplGen extends ADPsiElementImpl implements ADClassDeclaration {

  public ADClassDeclarationImplGen(@NotNull IElementType type) {
    super(type);
  }

  public void accept(@NotNull ADVisitor visitor) {
    visitor.visitClassDeclaration(this);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof ADVisitor) accept((ADVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public ADClassHeader getClassHeader() {
    return PsiTreeUtil.getChildOfType(this, ADClassHeader.class);
  }

  @Override
  @NotNull
  public List<ADMember> getMemberList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, ADMember.class);
  }

  @Override
  public @Nullable PsiClass resolvePsiClass() {
    return ADPsiImplUtil.resolvePsiClass(this);
  }

  @Override
  public @NotNull PsiElement getNavigationElement() {
    return ADPsiImplUtil.getNavigationElement(this);
  }

}
