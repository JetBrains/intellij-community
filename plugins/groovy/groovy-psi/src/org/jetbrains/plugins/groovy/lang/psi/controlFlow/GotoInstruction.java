// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ConditionInstruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl;

/**
 * @author Max Medvedev
 */
public abstract class GotoInstruction extends InstructionImpl {
  private final @NotNull ConditionInstruction myCondition;

  public GotoInstruction(@Nullable PsiElement element, @NotNull ConditionInstruction condition) {
    super(element);
    myCondition = condition;
  }

  public @NotNull ConditionInstruction getCondition() {
    return myCondition;
  }

  @Override
  protected @NotNull String getElementPresentation() {
    return " Positive goto instruction, condition=" + myCondition.num() + getElement();
  }
}
