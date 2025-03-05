// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.ElementPresentationUtil;
import com.intellij.ui.IconManager;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrClassInitializer;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;

import javax.swing.*;

public class GrClassInitializerImpl extends GroovyPsiElementImpl implements GrClassInitializer {

  public GrClassInitializerImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitClassInitializer(this);
  }

  @Override
  public String toString() {
    return "Class initializer";
  }

  @Override
  public @NotNull GrOpenBlock getBlock() {
    return findNotNullChildByClass(GrOpenBlock.class);
  }

  @Override
  public boolean isStatic() {
    return getModifierList().hasExplicitModifier(PsiModifier.STATIC);
  }


  @Override
  public PsiClass getContainingClass() {
    PsiElement parent = getParent();
    if (parent instanceof GrTypeDefinitionBody) {
      final PsiElement pparent = parent.getParent();
      if (pparent instanceof PsiClass) {
        return (PsiClass) pparent;
      }
    }
    return null;
  }

  @Override
  public GrMember[] getMembers() {
    return new GrMember[]{this};
  }

  @Override
  public @NotNull GrModifierList getModifierList() {
    return findNotNullChildByClass(GrModifierList.class);
  }

  @Override
  public boolean hasModifierProperty(@GrModifier.GrModifierConstant @NonNls @NotNull String name) {
    return getModifierList().hasModifierProperty(name);
  }

  @Override
  public @NotNull PsiCodeBlock getBody() {
    return PsiImplUtil.getOrCreatePsiCodeBlock(getBlock());
  }

  @Override
  protected @Nullable Icon getElementIcon(int flags) {
    return IconManager
      .getInstance().createLayeredIcon(this, JetgroovyIcons.Groovy.ClassInitializer, ElementPresentationUtil.getFlags(this, false));
  }
}
