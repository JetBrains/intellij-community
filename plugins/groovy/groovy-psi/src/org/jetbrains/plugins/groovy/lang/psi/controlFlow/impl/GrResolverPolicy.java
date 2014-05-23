/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

/**
 * @author Max Medvedev
 */
public class GrResolverPolicy implements GrControlFlowPolicy {
  @Override
  public boolean isReferenceAccepted(@NotNull GrReferenceExpression ref) {
    return !ref.isQualified();
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
