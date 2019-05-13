// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * @author ilyas
 */
public interface GrArgumentLabel extends GroovyPsiElement, GroovyReference {

  GrArgumentLabel[] EMPTY_ARRAY = new GrArgumentLabel[0];

  @NotNull
  PsiElement getNameElement();

  /**
   * @return expression which is put into parentheses.
   */
  @Nullable
  GrExpression getExpression();

  @Nullable
  String getName();

  PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException;

  @Nullable
  PsiType getExpectedArgumentType();

  @Nullable
  PsiType getLabelType();

  GrNamedArgument getNamedArgument();
}
