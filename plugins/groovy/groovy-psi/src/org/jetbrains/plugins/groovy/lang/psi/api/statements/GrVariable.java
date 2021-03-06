// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.util.ArrayFactory;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GrNamedElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 11.04.2007
 */
public interface GrVariable extends PsiVariable, GrNamedElement {
  GrVariable[] EMPTY_ARRAY = new GrVariable[0];
  ArrayFactory<GrVariable> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new GrVariable[count];

  @Override
  @NlsSafe @NotNull String getName();

  default @Nullable PsiType getInitializerType() {
    GrExpression initializer = getInitializerGroovy();
    return initializer == null ? null : initializer.getType();
  }

  @Nullable
  GrExpression getInitializerGroovy();

  void setType(@Nullable PsiType type) throws IncorrectOperationException;

  @Nullable
  GrTypeElement getTypeElementGroovy();

  @Nullable
  PsiType getTypeGroovy();

  @Nullable
  PsiType getDeclaredType();

  @Override
  @Nullable
  GrModifierList getModifierList();

  void setInitializerGroovy(@Nullable GrExpression initializer);

  @Override
  boolean hasModifierProperty(@GrModifier.GrModifierConstant @NonNls @NotNull String name);
}
