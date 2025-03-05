// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrIfStatement;

/**
 * @author Maxim.Medvedev
 */
public class IfEndInstruction extends InstructionImpl{
  public IfEndInstruction(GrIfStatement ifStatement) {
    super(ifStatement);
  }

  @Override
  public @Nullable GrIfStatement getElement() {
    return (GrIfStatement)super.getElement();
  }

  @Override
  protected @NotNull String getElementPresentation() {
    return "End element: " + myPsiElement;
  }
}
