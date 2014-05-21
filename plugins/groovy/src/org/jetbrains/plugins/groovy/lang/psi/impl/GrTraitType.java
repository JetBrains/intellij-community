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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeVisitor;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;

/**
 * Created by Max Medvedev on 20/05/14
 */
public class GrTraitType extends PsiType {

  private static final Logger LOG = Logger.getInstance(GrTraitType.class);

  private final GrExpression myOriginal;
  private final GlobalSearchScope myResolveScope;
  private final PsiClassType myExprType;
  private final PsiClassType myTraitType;


  public GrTraitType(@NotNull GrExpression original,
                     @NotNull PsiClassType exprType,
                     @NotNull PsiClassType traitType,
                     @NotNull GlobalSearchScope resolveScope) {
    super(PsiAnnotation.EMPTY_ARRAY);
    myOriginal = original;
    myResolveScope = resolveScope;
    myExprType = exprType;
    myTraitType = traitType;
  }

  @NotNull
  @Override
  public String getPresentableText() {
    return myExprType.getPresentableText() + " as " + myTraitType.getPresentableText();
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return myExprType.getCanonicalText();
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return myExprType.getCanonicalText() + " as " + myTraitType.getCanonicalText();
  }

  @Override
  public boolean isValid() {
    return myExprType.isValid() && myTraitType.isValid();
  }

  @Override
  public boolean equalsToText(@NotNull @NonNls String text) {
    return false;
  }

  @Override
  public <A> A accept(@NotNull PsiTypeVisitor<A> visitor) {
    return visitor.visitType(this);
  }

  @NotNull
  @Override
  public PsiType[] getSuperTypes() {
    return new PsiType[]{myExprType, myTraitType};
  }

  @NotNull
  @Override
  public GlobalSearchScope getResolveScope() {
    return myResolveScope;
  }

  @Nullable
  public GrTypeDefinition getMockTypeDefinition() {
    return CachedValuesManager.getCachedValue(myOriginal, new CachedValueProvider<GrTypeDefinition>() {
      @Nullable
      @Override
      public Result<GrTypeDefinition> compute() {
        return Result.create(buildMockTypeDefinition(), PsiModificationTracker.MODIFICATION_COUNT);
      }
    });
  }

  public PsiClassType getExprType() {
    return myExprType;
  }

  public PsiClassType getTraitType() {
    return myTraitType;
  }

  @Nullable
  private GrTypeDefinition buildMockTypeDefinition() {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myOriginal.getProject());
    try {
      GrTypeDefinition definition = factory.createTypeDefinition("class ___________Temp______ extends Super implements Trait {}");
      replaceReferenceWith(factory, definition.getExtendsClause(), myExprType);
      replaceReferenceWith(factory, definition.getImplementsClause(), myTraitType);
      return definition;
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  private static void replaceReferenceWith(@NotNull GroovyPsiElementFactory factory,
                                           @Nullable GrReferenceList clause,
                                           @NotNull PsiClassType type) {
    LOG.assertTrue(clause != null);
    GrCodeReferenceElement mockSuperReference = clause.getReferenceElementsGroovy()[0];

    GrClassTypeElement superTypeElement = ((GrClassTypeElement)factory.createTypeElement(type));
    GrCodeReferenceElement superReference = superTypeElement.getReferenceElement();
    mockSuperReference.replace(superReference);
  }

  @Nullable
  public static GrTraitType createTraitClassType(@NotNull GrSafeCastExpression safeCastExpression) {
    GrExpression operand = safeCastExpression.getOperand();
    PsiType exprType = operand.getType();
    if (!(exprType instanceof PsiClassType)) return null;

    GrTypeElement typeElement = safeCastExpression.getCastTypeElement();
    if (typeElement == null) return null;
    PsiType type = typeElement.getType();
    if (!GrTraitUtil.isTrait(PsiTypesUtil.getPsiClass(type))) return null;

    return new GrTraitType(safeCastExpression, ((PsiClassType)exprType), ((PsiClassType)type), safeCastExpression.getResolveScope());
  }

  public GrTraitType erasure() {
    PsiClassType exprType = (PsiClassType)TypeConversionUtil.erasure(myExprType);
    PsiClassType traitType = (PsiClassType)TypeConversionUtil.erasure(myTraitType);
    return new GrTraitType(myOriginal, exprType, traitType, myResolveScope);
  }

}
