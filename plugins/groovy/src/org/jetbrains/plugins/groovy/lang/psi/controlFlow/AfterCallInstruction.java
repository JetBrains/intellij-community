/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
  public final CallInstruction call;
  private RetInstruction myReturnInsn;

  public AfterCallInstruction(int num, CallInstruction call) {
    super(null, num);
    this.call = call;
  }

  public String toString() {
    return super.toString() + "AFTER CALL " + call.num();
  }

  public Iterable<? extends Instruction> allPred() {
    return Collections.singletonList(myReturnInsn);
  }

  public Iterable<? extends Instruction> pred(CallEnvironment env) {
    getStack(env, myReturnInsn).push(call);
    return Collections.singletonList(myReturnInsn);
  }

  protected String getElementPresentation() {
    return "";
  }

  public void setReturnInstruction(RetInstruction retInstruction) {
    myReturnInsn = retInstruction;
  }
}