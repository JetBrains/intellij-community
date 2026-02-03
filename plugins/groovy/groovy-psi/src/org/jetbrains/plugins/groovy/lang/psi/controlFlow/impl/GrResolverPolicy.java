// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

import static org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtilKt.isThisRef;

/**
 * @author Max Medvedev
 */
public final class GrResolverPolicy implements GrControlFlowPolicy {
  @Override
  public boolean isReferenceAccepted(@NotNull GrReferenceExpression ref) {
    return !ref.isQualified() || isThisRef(ref.getQualifierExpression());
  }

  @Override
  public boolean isVariableInitialized(@NotNull GrVariable variable) {
    return variable.getInitializerGroovy() != null || hasTupleInitializer(variable) || variable instanceof GrParameter;
  }

  private static boolean hasTupleInitializer(@NotNull GrVariable variable) {
    final PsiElement parent = variable.getParent();
    return parent instanceof GrVariableDeclaration && ((GrVariableDeclaration)parent).getTupleInitializer() != null;
  }

  public static GrResolverPolicy getInstance() {
    return INSTANCE;
  }

  private static final GrResolverPolicy INSTANCE = new GrResolverPolicy();
}
