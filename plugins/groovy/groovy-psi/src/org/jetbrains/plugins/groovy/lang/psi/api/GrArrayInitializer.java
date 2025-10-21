// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.api;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.List;

/**
 * Represents an array initializer expression.
 */
public interface GrArrayInitializer extends GrExpression {

  @NotNull
  PsiElement getLBrace();

  boolean isEmpty();

  /**
   * Returns the list of expressions initializing members of the .
   */
  @NotNull
  List<GrExpression> getExpressions();

  @Nullable
  PsiElement getRBrace();
}
