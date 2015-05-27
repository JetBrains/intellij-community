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
import com.intellij.codeInspection.dataFlow.instructions.TypeCastInstruction;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;

public class GrTypeCastInstruction<V extends GrInstructionVisitor<V>> extends TypeCastInstruction<V> {

  private final @NotNull GrTypeCastExpression castExpression;

  public GrTypeCastInstruction(@NotNull PsiType castTo, @NotNull GrTypeCastExpression expression) {
    super(castTo);
    castExpression = expression;
  }

  @NotNull
  @Override
  public GrTypeCastExpression getCastExpression() {
    return castExpression;
  }

  @Override
  public DfaInstructionState<V>[] accept(@NotNull DfaMemoryState stateBefore, @NotNull V visitor) {
    return visitor.visitTypeCastGroovy(this, stateBefore);
  }
}
