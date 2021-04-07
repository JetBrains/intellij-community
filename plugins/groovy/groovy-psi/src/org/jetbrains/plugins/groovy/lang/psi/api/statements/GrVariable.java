// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier.GrModifierConstant;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

public interface GrVariable extends PsiVariable, GrNamedElement {

  GrVariable[] EMPTY_ARRAY = new GrVariable[0];
  ArrayFactory<GrVariable> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new GrVariable[count];

  @Override
  @NlsSafe @NotNull String getName();

  /**
   * Returns corresponding type element, or {@code null} if type is not declared explicitly.
   * May return same element for different variables in tuple variable declaration.
   */
  @Nullable GrTypeElement getTypeElementGroovy();

  /**
   * Returns statically known type, or {@code null} if type is unknown.
   * May return non null value for variables without {@link #getTypeElementGroovy()}, e.g., vararg parameters or enum fields.
   */
  default @Nullable PsiType getDeclaredType() {
    GrTypeElement typeElement = getTypeElementGroovy();
    return typeElement != null ? typeElement.getType() : null;
  }

  /**
   * Returns initializer of this variable, or {@code null} if there is no explicit initializer.
   */
  @Nullable GrExpression getInitializerGroovy();

  /**
   * Returns type which initializes this variable.
   * May return non null value for variables without {@link #getInitializerGroovy()}, e.g., tuple variables.
   */
  default @Nullable PsiType getInitializerType() {
    GrExpression initializer = getInitializerGroovy();
    return initializer == null ? null : initializer.getType();
  }

  @Nullable PsiType getTypeGroovy();

  @Override
  @Nullable GrModifierList getModifierList();

  @Override
  boolean hasModifierProperty(@GrModifierConstant @NonNls @NotNull String name);

  void setType(@Nullable PsiType type) throws IncorrectOperationException;

  void setInitializerGroovy(@Nullable GrExpression initializer) throws IncorrectOperationException;
}
