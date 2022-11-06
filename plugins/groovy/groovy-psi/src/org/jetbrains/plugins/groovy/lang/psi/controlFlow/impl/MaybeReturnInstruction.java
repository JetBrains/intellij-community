// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import com.intellij.psi.PsiType;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class MaybeReturnInstruction extends InstructionImpl implements MaybeInterruptInstruction {
  MaybeReturnInstruction(GrExpression element) {
    super(element);
  }

  public String toString() {
    return super.toString() + " MAYBE_RETURN";
  }

  public boolean mayReturnValue() {
    GrExpression expression = (GrExpression) getElement();
    assert expression != null;
    final PsiType type = expression.getType();
    return !PsiType.VOID.equals(type) && !PsiUtil.isVoidMethodCall(expression);
  }

}
