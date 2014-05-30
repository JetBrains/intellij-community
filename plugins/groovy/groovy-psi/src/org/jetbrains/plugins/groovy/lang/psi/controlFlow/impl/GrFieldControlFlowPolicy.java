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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

/**
 * @author Max Medvedev
 */
public class GrFieldControlFlowPolicy implements GrControlFlowPolicy {
  @Override
  public boolean isReferenceAccepted(@NotNull GrReferenceExpression ref) {
    final GrExpression qualifier = ref.getQualifier();
    return (qualifier == null || isThisRef(qualifier)) && ref.resolve() instanceof GrField;
  }

  public static boolean isThisRef(@Nullable GrExpression expression) {
    return expression instanceof GrReferenceExpression &&
           ((GrReferenceExpression)expression).getQualifier() == null &&
           "this".equals(((GrReferenceExpression)expression).getReferenceName());
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
