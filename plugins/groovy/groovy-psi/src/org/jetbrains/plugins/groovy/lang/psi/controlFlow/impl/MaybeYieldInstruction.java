// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class MaybeYieldInstruction extends InstructionImpl implements MaybeInterruptInstruction {
  MaybeYieldInstruction(GrExpression element) {
    super(element);
  }

  public String toString() {
    return super.toString() + " MAYBE_YIELD";
  }

}
