// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author ven
 */
public interface GrSafeCastExpression extends GrExpression, GroovyReference {

  @Nullable
  GrTypeElement getCastTypeElement();

  @NotNull
  GrExpression getOperand();

  @NotNull
  PsiElement getOperationToken();
}
