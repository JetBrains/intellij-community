// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl;

import java.util.Collections;

public class CallInstruction extends InstructionImpl {
  private final InstructionImpl myCallee;

  public CallInstruction(InstructionImpl callee) {
    super(null);
    myCallee = callee;
  }

  @Override
  public @NonNls String toString() {
    return super.toString() + " CALL " + myCallee.num();
  }

  @Override
  public @NotNull Iterable<Instruction> successors(@NotNull CallEnvironment environment) {
    environment.callStack(myCallee).push(this);
    return Collections.singletonList(myCallee);
  }

  @Override
  public @NotNull Iterable<Instruction> allSuccessors() {
    return Collections.singletonList(myCallee);
  }

  @Override
  protected @NotNull String getElementPresentation() {
    return "";
  }
}
