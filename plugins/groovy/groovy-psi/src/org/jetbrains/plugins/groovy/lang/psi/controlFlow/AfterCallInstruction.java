/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang.psi.controlFlow;

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

  public String toString() {
    return super.toString() + "AFTER CALL " + myCall.num();
  }

  @Override
  public Iterable<? extends Instruction> allPredecessors() {
    return Collections.singletonList(myReturnInstruction);
  }

  @Override
  public Iterable<? extends Instruction> predecessors(CallEnvironment environment) {
    environment.callStack(myReturnInstruction).push(myCall);
    return Collections.singletonList(myReturnInstruction);
  }

  @Override
  protected String getElementPresentation() {
    return "";
  }

  public void setReturnInstruction(ReturnInstruction returnInstruction) {
    myReturnInstruction = returnInstruction;
  }
}
