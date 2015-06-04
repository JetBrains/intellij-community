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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class GrDereferenceInstruction extends GrInstruction {

  private final @NotNull GrExpression myExpression;

  public GrDereferenceInstruction(@NotNull GrExpression expression) {
    myExpression = expression;
  }

  @NotNull
  public GrExpression getExpression() {
    return myExpression;
  }

  @Override
  public DfaInstructionState[] acceptGroovy(@NotNull DfaMemoryState state, @NotNull GrInstructionVisitor visitor) {
    return visitor.visitDereference(this, state);
  }

  @Override
  public String toString() {
    return "DEREFERENCE";
  }
}
