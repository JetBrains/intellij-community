// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InstructionImpl;

import java.util.Collections;

/**
 * @author ven
 */
public class CallInstruction extends InstructionImpl {
  private final InstructionImpl myCallee;

  public CallInstruction(InstructionImpl callee) {
    super(null);
    myCallee = callee;
  }

  @NonNls
  public String toString() {
    return super.toString() + " CALL " + myCallee.num();
  }

  @NotNull
  @Override
  public Iterable<Instruction> successors(@NotNull CallEnvironment environment) {
    environment.callStack(myCallee).push(this);
    return Collections.singletonList(myCallee);
  }

  @NotNull
  @Override
  public Iterable<Instruction> allSuccessors() {
    return Collections.singletonList(myCallee);
  }

  @NotNull
  @Override
  protected String getElementPresentation() {
    return "";
  }
}
