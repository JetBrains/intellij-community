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
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GrMemberReferenceInstruction<V extends GrInstructionVisitor<V>> extends Instruction<V> {

  @NotNull
  private final GrExpression myExpression;
  @NotNull
  private final DfaValue myValue;
  private final boolean myIsSafe;

  public GrMemberReferenceInstruction(@NotNull GrExpression expression, DfaValue value, boolean isSafe) {
    myExpression = expression;
    myValue = value;
    myIsSafe = isSafe;
  }

  @NotNull
  public GrExpression getExpression() {
    return myExpression;
  }

  @NotNull
  public DfaValue getValue() {
    return myValue;
  }

  public boolean isSafe() {
    return myIsSafe;
  }

  @Override
  public DfaInstructionState<V>[] accept(@NotNull DfaMemoryState stateBefore, @NotNull V visitor) {
    return visitor.visitMemberReference(this, stateBefore);
  }

  @Override
  public String toString() {
    return "MEMBER_REFERENCE: " + getExpression().getText();
  }
}
