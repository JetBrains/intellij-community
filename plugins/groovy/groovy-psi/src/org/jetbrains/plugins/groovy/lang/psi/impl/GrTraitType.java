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
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class GrTraitType extends PsiType {

  private final @NotNull PsiIntersectionType myDelegate;
  private final @NotNull PsiType myExprType;
  private final @NotNull List<PsiType> myTraitTypes;

  private GrTraitType(@NotNull PsiIntersectionType delegate) {
    super(PsiAnnotation.EMPTY_ARRAY);
    myDelegate = delegate;
    myExprType = delegate.getConjuncts()[0];
    myTraitTypes = ContainerUtil.newArrayList(delegate.getConjuncts(), 1, delegate.getConjuncts().length);
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
  public PsiType[] getConjuncts() {
    return myDelegate.getConjuncts();
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
  public String getCanonicalText() {
    return myDelegate.getCanonicalText();
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

  @Override
  public boolean isValid() {
    return myDelegate.isValid();
  }

  @Override
  public boolean equalsToText(@NotNull @NonNls String text) {
    return myDelegate.equalsToText(text);
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return myDelegate.accept(visitor);
  }

  @Nullable
  @Override
  public GlobalSearchScope getResolveScope() {
    return myDelegate.getResolveScope();
  }

  @NotNull
  @Override
  public PsiType[] getSuperTypes() {
    return myDelegate.getSuperTypes();
  }

  // todo move this method to org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.types.GrSafeCastExpressionImpl
  @Nullable
  public static PsiType createTraitType(@NotNull GrSafeCastExpression safeCastExpression) {
    GrExpression operand = safeCastExpression.getOperand();
    PsiType exprType = operand.getType();
    if (!(exprType instanceof PsiClassType) && !(exprType instanceof GrTraitType)) return null;

    GrTypeElement typeElement = safeCastExpression.getCastTypeElement();
    if (typeElement == null) return null;
    PsiType type = typeElement.getType();
    if (!GrTraitUtil.isTrait(PsiTypesUtil.getPsiClass(type))) return null;

    return createTraitType(exprType, ContainerUtil.newSmartList(type));
  }

  @NotNull
  public static PsiType createTraitType(@NotNull PsiType type, @NotNull List<PsiType> traits) {
    return createTraitType(ContainerUtil.prepend(traits, type instanceof GrTraitType ? ((GrTraitType)type).myDelegate : type));
  }

  @NotNull
  public static PsiType createTraitType(@NotNull List<PsiType> types) {
    return createTraitType(types.toArray(PsiType.createArray(types.size())));
  }

  @NotNull
  public static PsiType createTraitType(@NotNull PsiType[] types) {
    final Set<PsiType> flattened = PsiIntersectionType.flatten(types, new LinkedHashSet<PsiType>() {
      @Override
      public boolean add(PsiType type) {
        remove(type);
        return super.add(type);
      }
    });
    final PsiType[] conjuncts = flattened.toArray(PsiType.createArray(flattened.size()));
    if (conjuncts.length == 1) {
      return conjuncts[0];
    }
    else {
      return new GrTraitType((PsiIntersectionType)PsiIntersectionType.createIntersection(false, conjuncts));
    }
  }
}
