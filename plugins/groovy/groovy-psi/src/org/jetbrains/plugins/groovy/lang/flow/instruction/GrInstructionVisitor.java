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
package org.jetbrains.plugins.groovy.lang.flow.instruction;

import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import org.jetbrains.plugins.groovy.lang.flow.GrDataFlowRunner;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public abstract class GrInstructionVisitor extends InstructionVisitor {

  protected final GrDataFlowRunner myRunner;

  public GrInstructionVisitor(GrDataFlowRunner runner) {
    super(runner);
    myRunner = runner;
  }

  public DfaInstructionState[] visitAssignGroovy(GrAssignInstruction instruction, DfaMemoryState state) {
    state.pop();
    state.push(state.pop());
    return nextInstruction(instruction, state);
  }

  public DfaInstructionState[] visitMethodCallGroovy(GrMethodCallInstruction instruction, DfaMemoryState state) {
    boolean wasNamed = false;
    for (final GrNamedArgument ignored : instruction.getNamedArguments()) {
      state.pop();
      wasNamed = true;
    }
    if (wasNamed) {
      state.push(DfaUnknownValue.getInstance());
    }
    for (final GrExpression ignored : instruction.getExpressionArguments()) {
      state.pop();
    }
    for (final GrClosableBlock ignored : instruction.getClosureArguments()) {
      state.pop();
    }
    state.pop(); //qualifier
    state.push(DfaUnknownValue.getInstance());
    return nextInstruction(instruction, state);
  }

  public DfaInstructionState[] visitMemberReference(GrMemberReferenceInstruction instruction, DfaMemoryState state) {
    state.pop();
    return nextInstruction(instruction, state);
  }

  public DfaInstructionState[] visitTypeCastGroovy(GrTypeCastInstruction instruction, DfaMemoryState state) {
    return nextInstruction(instruction, state);
  }
}



