// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl;

import java.util.Collections;

/**
 * @author ven
 */
public class AfterCallInstruction extends InstructionImpl {
  public final CallInstruction myCall;
  private ReturnInstruction myReturnInstruction;

  public AfterCallInstruction(CallInstruction call) {
    super(null);
    this.myCall = call;
  }

  @NonNls
  public String toString() {
    return super.toString() + "AFTER CALL " + myCall.num();
  }

  @NotNull
  @Override
  public Iterable<Instruction> allPredecessors() {
    return Collections.singletonList(myReturnInstruction);
  }

  @NotNull
  @Override
  public Iterable<Instruction> predecessors(@NotNull CallEnvironment environment) {
    environment.callStack(myReturnInstruction).push(myCall);
    return Collections.singletonList(myReturnInstruction);
  }

  @NotNull
  @Override
  protected String getElementPresentation() {
    return "";
  }

  public void setReturnInstruction(ReturnInstruction returnInstruction) {
    myReturnInstruction = returnInstruction;
  }
}
