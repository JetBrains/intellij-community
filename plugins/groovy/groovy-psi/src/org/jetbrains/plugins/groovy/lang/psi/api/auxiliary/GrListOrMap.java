// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.api.auxiliary;

import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.psi.PsiArrayInitializerMemberValue;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrNamedArgumentsOwner;
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyConstructorReference;

public interface GrListOrMap extends UserDataHolderEx, Cloneable, Iconable, PsiElement, NavigationItem, GrExpression,
                                     PsiArrayInitializerMemberValue, GrNamedArgumentsOwner {
  /*
   * Use for list
   */
  @Override
  GrExpression @NotNull [] getInitializers();

  boolean isMap();

  @Override
  boolean isEmpty();

  @NotNull
  PsiElement getLBrack();

  @Nullable
  PsiElement getRBrack();

  @Nullable
  GroovyConstructorReference getConstructorReference();
}
