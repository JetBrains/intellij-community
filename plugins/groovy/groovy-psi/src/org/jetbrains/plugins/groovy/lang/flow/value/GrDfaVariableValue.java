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
import com.intellij.codeInspection.dataFlow.Nullness;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GrDfaVariableValue extends DfaVariableValue {

  public static class FactoryImpl extends Factory {

    protected FactoryImpl(DfaValueFactory factory) {
      super(factory);
    }

    @Override
    protected DfaVariableValue createConcrete(@NotNull PsiModifierListOwner variable,
                                              @Nullable PsiType varType,
                                              @Nullable DfaVariableValue qualifier,
                                              boolean isNegated) {
      return new GrDfaVariableValue(myFactory, variable, varType, qualifier, isNegated);
    }
  }

  private GrDfaVariableValue(DfaValueFactory factory,
                             @NotNull PsiModifierListOwner variable,
                             @Nullable PsiType varType,
                             @Nullable DfaVariableValue qualifier, boolean isNegated) {
    super(factory, variable, varType, qualifier, isNegated);
  }

  @Override
  protected Nullness calcInherentNullability() {
    PsiModifierListOwner var = getPsiVariable();
    Nullness nullability = DfaPsiUtil.getElementNullability(getVariableType(), var);
    if (nullability != Nullness.UNKNOWN) {
      return nullability;
    }

    Nullness defaultNullability = myFactory.isUnknownMembersAreNullable() ? Nullness.NULLABLE : Nullness.UNKNOWN;

    //if (var instanceof PsiParameter && var.getParent() instanceof PsiForeachStatement) {
    //  PsiExpression iteratedValue = ((PsiForeachStatement)var.getParent()).getIteratedValue();
    //  if (iteratedValue != null) {
    //    PsiType itemType = JavaGenericsUtil.getCollectionItemType(iteratedValue);
    //    if (itemType != null) {
    //      return DfaPsiUtil.getElementNullability(itemType, var);
    //    }
    //  }
    //}

    //if (var instanceof PsiField && DfaPsiUtil.isFinalField((PsiVariable)var) && myFactory.isHonorFieldInitializers()) {
    //  List<PsiExpression> initializers = DfaPsiUtil.findAllConstructorInitializers((PsiField)var);
    //  if (initializers.isEmpty()) {
    //    return defaultNullability;
    //  }
    //
    //  boolean hasUnknowns = false;
    //  for (PsiExpression expression : initializers) {
    //    if (!(expression instanceof PsiReferenceExpression)) {
    //      hasUnknowns = true;
    //      continue;
    //    }
    //    PsiElement target = ((PsiReferenceExpression)expression).resolve();
    //    if (!(target instanceof PsiParameter)) {
    //      hasUnknowns = true;
    //      continue;
    //    }
    //    if (NullableNotNullManager.isNullable((PsiParameter)target)) {
    //      return Nullness.NULLABLE;
    //    }
    //    if (!NullableNotNullManager.isNotNull((PsiParameter)target)) {
    //      hasUnknowns = true;
    //    }
    //  }
    //
    //  if (hasUnknowns) {
    //    if (DfaPsiUtil.isInitializedNotNull((PsiField)var)) {
    //      return Nullness.NOT_NULL;
    //    }
    //    return defaultNullability;
    //  }
    //
    //  return Nullness.NOT_NULL;
    //}

    return defaultNullability;
  }

  @Override
  public boolean isFlushableByCalls() {
    return false;
  }
}
