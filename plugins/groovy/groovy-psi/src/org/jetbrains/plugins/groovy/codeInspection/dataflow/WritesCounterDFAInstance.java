// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.dataflow;

import com.intellij.psi.PsiElement;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
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

public final class WritesCounterDFAInstance implements DfaInstance<Object2IntMap<GrVariable>> {
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
  public Object2IntMap<GrVariable> fun(@NotNull Object2IntMap<GrVariable> map, @NotNull Instruction instruction) {
    if (!(instruction instanceof ReadWriteVariableInstruction)) return map;

    final ReadWriteVariableInstruction rwInstruction = (ReadWriteVariableInstruction)instruction;
    if (!rwInstruction.isWrite()) return map;

    final GrVariable variable = getVariable(instruction.getElement());
    if (variable == null) return map;

    int currentVal = map.getInt(variable);
    if (currentVal == 2) return map;

    if (currentVal == 0 || currentVal == 1 && !(variable.getParent() instanceof GrForInClause)) currentVal++;
    Object2IntMap<GrVariable> newMap = new Object2IntOpenHashMap<>(map);
    newMap.put(variable, currentVal);
    return newMap;
  }
}
