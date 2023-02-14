// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.inline;

import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.GroovyControlFlow;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Max Medvedev
 */
public class InlineLocalVarSettings {
  private final GrExpression myInitializer;
  private final int myWriteInstructionNumber;
  private final GroovyControlFlow myFlow;

  public InlineLocalVarSettings(GrExpression initializer, int writeInstructionNumber, GroovyControlFlow flow) {
    myWriteInstructionNumber = writeInstructionNumber;
    myFlow = flow;
    final PsiElement psiElement = PsiUtil.skipParentheses(initializer, false);
    if (psiElement instanceof GrExpression) {
      myInitializer = (GrExpression)psiElement;
    }
    else {
      myInitializer = initializer;
    }
  }

  public GrExpression getInitializer() {
    return myInitializer;
  }

  public int getWriteInstructionNumber() {
    return myWriteInstructionNumber;
  }

  public GroovyControlFlow getFlow() {
    return myFlow;
  }
}
