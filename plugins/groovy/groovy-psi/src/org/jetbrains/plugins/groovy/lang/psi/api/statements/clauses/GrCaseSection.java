// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrDeclarationHolder;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrVariableDeclarationOwner;

/**
 * @author ilyas
 */
public interface GrCaseSection extends GroovyPsiElement, GrVariableDeclarationOwner, GrStatementOwner, GrDeclarationHolder {
  /**
   * Returns expressions that are listed between case label and arrow/colon.
   */
  @Nullable GrExpression @NotNull [] getExpressions();

  boolean isDefault();

  /**
   * Returns the arrow element of case section.
   * If {@link GrCaseSection#getColon()} returns not-null, then this method returns null.
   */
  @Nullable
  PsiElement getArrow();

  /**
   * Returns the colon element of case section.
   * If {@link GrCaseSection#getArrow()} returns not-null, then this method returns null.
   */
  @Nullable
  PsiElement getColon();
}
