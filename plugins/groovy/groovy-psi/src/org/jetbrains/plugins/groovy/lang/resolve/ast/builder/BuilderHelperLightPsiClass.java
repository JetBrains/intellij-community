// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve.ast.builder;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.impl.light.LightPsiClassBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

public class BuilderHelperLightPsiClass extends LightPsiClassBuilder {

  public BuilderHelperLightPsiClass(@NotNull GrTypeDefinition containingClass, @NotNull String name) {
    super(containingClass, name);
    setScope(containingClass);
    setContainingClass(containingClass);
    setOriginInfo(BuilderAnnotationContributor.ORIGIN_INFO);
    getModifierList().addModifier(PsiModifier.STATIC);
  }

  @Override
  public PsiElement getParent() {
    return getContainingClass();
  }

  @Override
  public @NotNull PsiClass getContainingClass() {
    //noinspection ConstantConditions
    return super.getContainingClass();
  }

  @Override
  public PsiFile getContainingFile() {
    return getContainingClass().getContainingFile();
  }
}
