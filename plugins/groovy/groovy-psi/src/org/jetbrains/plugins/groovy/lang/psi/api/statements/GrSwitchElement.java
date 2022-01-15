// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrCaseSection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public interface GrSwitchElement extends GroovyPsiElement {
  /**
   * Returns the selector on which the switch is performed.
   */
  @Nullable
  GrExpression getCondition();

  GrCaseSection @NotNull [] getCaseSections();

  @Nullable PsiElement getRParenth();

  @Nullable PsiElement getLBrace();
}
