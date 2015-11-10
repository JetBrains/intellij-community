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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiIntersectionType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class GrTraitType extends PsiIntersectionType {

  private final @NotNull PsiType myExprType;
  private final @NotNull List<PsiType> myTraitTypes;

  private GrTraitType(@NotNull PsiType[] types) {
    super(types);
    myExprType = types[0];
    myTraitTypes = ContainerUtil.newArrayList(types, 1, types.length);
  }

  @NotNull
  public PsiType getExprType() {
    return myExprType;
  }

  @NotNull
  public List<PsiType> getTraitTypes() {
    return myTraitTypes;
  }

  @NotNull
  @Override
  public String getPresentableText() {
    return myExprType.getPresentableText() + " as " + StringUtil.join(ContainerUtil.map(myTraitTypes, new Function<PsiType, String>() {
      @Override
      public String fun(PsiType type) {
        return type.getPresentableText();
      }
    }), ", ");
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return myExprType.getCanonicalText() + " as " + StringUtil.join(ContainerUtil.map(myTraitTypes, new Function<PsiType, String>() {
      @Override
      public String fun(PsiType type) {
        return type.getInternalCanonicalText();
      }
    }), ", ");
  }

  // todo move this method to org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.types.GrSafeCastExpressionImpl
  @Nullable
  public static PsiType createTraitClassType(@NotNull GrSafeCastExpression safeCastExpression) {
    GrExpression operand = safeCastExpression.getOperand();
    PsiType exprType = operand.getType();
    if (!(exprType instanceof PsiClassType) && !(exprType instanceof GrTraitType)) return null;

    GrTypeElement typeElement = safeCastExpression.getCastTypeElement();
    if (typeElement == null) return null;
    PsiType type = typeElement.getType();
    if (!GrTraitUtil.isTrait(PsiTypesUtil.getPsiClass(type))) return null;

    return createTraitClassType(exprType, ContainerUtil.newSmartList(type));
  }

  public static PsiType createTraitClassType(@NotNull PsiType type, @NotNull List<PsiType> traits) {
    return createTraitClassType(ContainerUtil.prepend(traits, type));
  }

  public static PsiType createTraitClassType(@NotNull List<PsiType> types) {
    final Set<PsiType> flattened = flatten(types.toArray(createArray(types.size())), new LinkedHashSet<PsiType>() {
      @Override
      public boolean add(PsiType type) {
        remove(type);
        return super.add(type);
      }
    });
    final PsiType[] conjuncts = flattened.toArray(createArray(flattened.size()));
    if (conjuncts.length == 1) return conjuncts[0];
    return new GrTraitType(conjuncts);
  }
}
