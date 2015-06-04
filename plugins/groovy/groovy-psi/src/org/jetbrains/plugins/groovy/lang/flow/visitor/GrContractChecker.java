/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.flow.visitor;

import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.MethodContract.ValueConstraint;
import com.intellij.codeInspection.dataFlow.instructions.CheckReturnValueInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.instructions.ReturnInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.PsiElement;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.flow.GrDataFlowRunner;
import org.jetbrains.plugins.groovy.lang.flow.instruction.GrMethodCallInstruction;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class GrContractChecker extends GrDataFlowRunner {

  private final GrMethod myMethod;
  private final MethodContract myContract;
  private final boolean myOnTheFly;
  private final Set<PsiElement> myViolations = ContainerUtil.newHashSet();
  private final Set<PsiElement> myNonViolations = ContainerUtil.newHashSet();
  private final Set<PsiElement> myFailures = ContainerUtil.newHashSet();

  public GrContractChecker(@NotNull GrMethod method, @NotNull MethodContract contract, boolean onTheFly) {
    super(false, false);
    myMethod = method;
    myContract = contract;
    myOnTheFly = onTheFly;
  }

  @Override
  protected boolean shouldCheckTimeLimit() {
    if (!myOnTheFly) return false;
    return super.shouldCheckTimeLimit();
  }

  @NotNull
  @Override
  protected DfaInstructionState[] acceptInstruction(InstructionVisitor visitor, DfaInstructionState instructionState) {
    final DfaMemoryState memState = instructionState.getMemoryState();
    if (memState.isEphemeral()) return DfaInstructionState.EMPTY_ARRAY;

    final Instruction instruction = instructionState.getInstruction();

    if (instruction instanceof CheckReturnValueInstruction) {
      final PsiElement anchor = ((CheckReturnValueInstruction)instruction).getReturn();
      final DfaValue retValue = memState.pop();
      if (ContractChecker.breaksContract(getFactory(), retValue, myContract.returnValue, memState)) {
        myViolations.add(anchor);
      }
      else {
        myNonViolations.add(anchor);
      }
      return InstructionVisitor.nextInstruction(instruction, this, memState);
    }
    else if (instruction instanceof ReturnInstruction) {
      if (((ReturnInstruction)instruction).isViaException() && myContract.returnValue != ValueConstraint.NOT_NULL_VALUE) {
        ContainerUtil.addIfNotNull(myFailures, ((ReturnInstruction)instruction).getAnchor());
      }
    }
    else if (instruction instanceof GrMethodCallInstruction && myContract.returnValue == ValueConstraint.THROW_EXCEPTION) {
      ContainerUtil.addIfNotNull(myFailures, ((GrMethodCallInstruction)instruction).getCall());
      return DfaInstructionState.EMPTY_ARRAY;
    }

    return super.acceptInstruction(visitor, instructionState);
  }


  public Map<PsiElement, String> getErrors() {
    HashMap<PsiElement, String> errors = ContainerUtil.newHashMap();
    for (PsiElement element : myViolations) {
      if (!myNonViolations.contains(element)) {
        errors.put(element, "Contract clause '" + myContract + "' is violated");
      }
    }

    if (myContract.returnValue != ValueConstraint.THROW_EXCEPTION) {
      for (PsiElement element : myFailures) {
        errors.put(element, "Contract clause '" +
                            myContract +
                            "' is violated: exception might be thrown instead of returning " +
                            myContract.returnValue);
      }
    }
    else if (myFailures.isEmpty() && errors.isEmpty()) {
      PsiElement nameIdentifier = myMethod.getNameIdentifierGroovy();
      errors.put(nameIdentifier, "Contract clause '" + myContract + "' is violated: no exception is thrown");
    }

    return errors;
  }
}
