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
package org.jetbrains.plugins.groovy.codeInspection.dataflow;

import com.intellij.psi.PsiElement;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.clauses.GrForInClause;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.Instruction;
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.ReadWriteVariableInstruction;
import org.jetbrains.plugins.groovy.lang.psi.dataFlow.DfaInstance;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

public class WritesCounterDFAInstance implements DfaInstance<TObjectIntHashMap<GrVariable>> {

  @Contract("null -> null")
  @Nullable
  private static GrVariable getVariable(@Nullable PsiElement instructionElement) {
    final GrVariable variable;
    if (instructionElement instanceof GrReferenceExpression) {
      final PsiElement resolved = ((GrReferenceExpression)instructionElement).resolve();
      variable = resolved instanceof GrVariable ? (GrVariable)resolved : null;
    }
    else if (instructionElement instanceof GrVariable) {
      variable = (GrVariable)instructionElement;
    }
    else {
      variable = null;
    }
    return variable != null && PsiUtil.isLocalOrParameter(variable) ? variable : null;
  }

  @Override
  public void fun(TObjectIntHashMap<GrVariable> map, Instruction instruction) {
    if (!(instruction instanceof ReadWriteVariableInstruction)) return;

    final ReadWriteVariableInstruction rwInstruction = (ReadWriteVariableInstruction)instruction;
    if (!rwInstruction.isWrite()) return;

    final GrVariable variable = getVariable(instruction.getElement());
    if (variable == null) return;

    int currentVal = map.get(variable);
    if (currentVal == 2) return;

    if (currentVal == 0 || currentVal == 1 && !(variable.getParent() instanceof GrForInClause)) currentVal++;
    map.put(variable, currentVal);
  }

  @NotNull
  @Override
  public TObjectIntHashMap<GrVariable> initial() {
    return new TObjectIntHashMap<>();
  }

  @Override
  public boolean isForward() {
    return true;
  }
}
