// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.ConditionInstruction;

/**
 * @author Max Medvedev
 */
public class NegatingGotoInstruction extends GotoInstruction {
  public NegatingGotoInstruction(@Nullable PsiElement element, @NotNull ConditionInstruction condition) {
    super(element, condition);
  }

  @Override
  protected @NotNull String getElementPresentation() {
    return " Negating goto instruction, condition=" + getCondition().num() + getElement();
  }
}
