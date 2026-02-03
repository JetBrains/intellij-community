// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl;

import java.util.Collections;

public class AfterCallInstruction extends InstructionImpl {
  public final CallInstruction myCall;
  private ReturnInstruction myReturnInstruction;

  public AfterCallInstruction(CallInstruction call) {
    super(null);
    this.myCall = call;
  }

  @Override
  public @NonNls String toString() {
    return super.toString() + "AFTER CALL " + myCall.num();
  }

  @Override
  public @NotNull Iterable<Instruction> allPredecessors() {
    return Collections.singletonList(myReturnInstruction);
  }

  @Override
  public @NotNull Iterable<Instruction> predecessors(@NotNull CallEnvironment environment) {
    environment.callStack(myReturnInstruction).push(myCall);
    return Collections.singletonList(myReturnInstruction);
  }

  @Override
  protected @NotNull String getElementPresentation() {
    return "";
  }

  public void setReturnInstruction(ReturnInstruction returnInstruction) {
    myReturnInstruction = returnInstruction;
  }
}
