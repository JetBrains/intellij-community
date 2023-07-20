// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import static org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtilKt.isThisRef;

/**
 * @author Max Medvedev
 */
public final class GrAllVarsInitializedPolicy implements GrControlFlowPolicy {
  private static final GrControlFlowPolicy INSTANCE = new GrAllVarsInitializedPolicy();

  public static GrControlFlowPolicy getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isReferenceAccepted(@NotNull GrReferenceExpression ref) {
    return !ref.isQualified() || isThisRef(ref.getQualifierExpression());
  }

  @Override
  public boolean isVariableInitialized(@NotNull GrVariable variable) {
    return true;
  }
}
