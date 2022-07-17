// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.java.decompiler.modules.decompiler;

import org.jetbrains.java.decompiler.modules.decompiler.exps.Exprent;

import java.util.ArrayList;
import java.util.List;

public class PrimitiveExpressionList {
  private final List<Exprent> expressions = new ArrayList<>();
  private final ExpressionStack stack;

  public PrimitiveExpressionList() {
    this(new ExpressionStack());
  }

  private PrimitiveExpressionList(ExpressionStack stack) {
    this.stack = stack;
  }

  public PrimitiveExpressionList copy() {
    return new PrimitiveExpressionList(stack.copy());
  }

  public List<Exprent> getExpressions() {
    return expressions;
  }

  public ExpressionStack getStack() {
    return stack;
  }
}
