// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import static org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtilKt.isThisRef;

/**
 * @author Max Medvedev
 */
public final class GrFieldControlFlowPolicy implements GrControlFlowPolicy {
  @Override
  public boolean isReferenceAccepted(@NotNull GrReferenceExpression ref) {
    final GrExpression qualifier = ref.getQualifier();
    return (qualifier == null || isThisRef(qualifier)) && ref.resolve() instanceof GrField;
  }

  @Override
  public boolean isVariableInitialized(@NotNull GrVariable variable) {
    return false;
  }

  public static GrFieldControlFlowPolicy getInstance() {
    return INSTANCE;
  }

  private static final GrFieldControlFlowPolicy INSTANCE = new GrFieldControlFlowPolicy();
}
