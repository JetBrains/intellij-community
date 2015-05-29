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
package org.jetbrains.plugins.groovy.lang.flow.value;

import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.FieldNullabilityCalculator;
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;

public class GrDfaVariableValue extends DfaVariableValue {

  public static class FactoryImpl extends Factory {

    protected FactoryImpl(@NotNull DfaValueFactory factory) {
      super(factory);
    }

    @NotNull
    @Override
    protected DfaVariableValue createConcrete(@NotNull PsiModifierListOwner variable,
                                              @Nullable PsiType varType,
                                              @Nullable DfaVariableValue qualifier,
                                              boolean isNegated) {
      return new GrDfaVariableValue(myFactory, variable, varType, qualifier, isNegated);
    }
  }

  private GrDfaVariableValue(@NotNull DfaValueFactory factory,
                             @NotNull PsiModifierListOwner variable,
                             @Nullable PsiType varType,
                             @Nullable DfaVariableValue qualifier, boolean isNegated) {
    super(factory, variable, varType, qualifier, isNegated);
  }

  @Override
  protected Nullness calcInherentNullability() {
    final PsiModifierListOwner var = getPsiVariable();
    final Nullness nullability = DfaPsiUtil.getElementNullability(
      getVariableType(),
      var instanceof GrAccessorMethod ? ((GrAccessorMethod)var).getProperty() : var
    );
    if (nullability != Nullness.UNKNOWN) {
      return nullability;
    }

    if (var instanceof PsiField && DfaPsiUtil.isFinalField((PsiVariable)var) && myFactory.isHonorFieldInitializers()) {
      final Nullness fieldNullability = FieldNullabilityCalculator.calculateNullability((PsiField)var);
      if (fieldNullability != Nullness.UNKNOWN) {
        return fieldNullability;
      }
    }

    return myFactory.isUnknownMembersAreNullable() ? Nullness.NULLABLE : Nullness.UNKNOWN;
  }

  @Override
  public boolean isFlushableByCalls() {
    if (myVariable instanceof GrVariable && !(myVariable instanceof GrField) || myVariable instanceof GrParameter) return false;
    if (myVariable instanceof GrVariable && myVariable.hasModifierProperty(PsiModifier.FINAL)) {
      return myQualifier != null && myQualifier.isFlushableByCalls();
    }
    return true;
  }
}
