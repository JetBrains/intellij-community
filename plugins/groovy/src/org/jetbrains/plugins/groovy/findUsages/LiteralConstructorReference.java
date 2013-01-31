/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.gpp.GppTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author peter
 */
public class LiteralConstructorReference extends PsiReferenceBase.Poly<GrListOrMap> {
  @NotNull
  private final PsiClassType myExpectedType;

  public LiteralConstructorReference(@NotNull GrListOrMap element, @NotNull PsiClassType constructedClassType) {
    super(element, TextRange.from(0, 0), false);
    myExpectedType = constructedClassType;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return getElement();
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return getElement();
  }

  @NotNull
  public PsiClassType getConstructedClassType() {
    return myExpectedType;
  }

  @Nullable
  public static PsiClassType getTargetConversionType(@NotNull final GrExpression expression) {
    //todo hack
    final PsiElement parent = PsiUtil.skipParentheses(expression.getParent(), true);
    
    PsiType type = null;
    if (parent instanceof GrSafeCastExpression) {
      type = ((GrSafeCastExpression)parent).getType();
    }
    else if (parent instanceof GrTypeCastExpression) {
      type = ((GrTypeCastExpression)parent).getType();
    }
    else if (parent instanceof GrAssignmentExpression &&
        PsiTreeUtil.isAncestor(((GrAssignmentExpression)parent).getRValue(), expression, false)) {
      final PsiElement lValue = PsiUtil.skipParentheses(((GrAssignmentExpression)parent).getLValue(), false);
      if (lValue instanceof GrReferenceExpression) {
        type = ((GrReferenceExpression)lValue).getNominalType();
      }
    }
    else if (parent instanceof GrVariable) {
      type = ((GrVariable)parent).getDeclaredType();
    }
    else if (parent instanceof GrArgumentList && GppTypeConverter.hasTypedContext(parent)) {
      for (PsiType expected : GroovyExpectedTypesProvider.getDefaultExpectedTypes(expression)) {
        expected = filterOutTrashTypes(expected);
        if (expected != null) return (PsiClassType)expected;
      }
    }
    else if (parent instanceof GrNamedArgument) {  //possible default constructor named arg
      for (PsiType expected : GroovyExpectedTypesProvider.getDefaultExpectedTypes(expression)) {
        expected = filterOutTrashTypes(expected);
        if (expected != null) return (PsiClassType)expected;
      }
    }
    else {
      final GrControlFlowOwner controlFlowOwner = ControlFlowUtils.findControlFlowOwner(expression);
      if (controlFlowOwner instanceof GrOpenBlock && controlFlowOwner.getParent() instanceof GrMethod) {
        if (ControlFlowUtils.isReturnValue(expression, controlFlowOwner)) {
          type = ((GrMethod)controlFlowOwner.getParent()).getReturnType();
        }
      }
    }

    return filterOutTrashTypes(type);
  }

  @Nullable
  private static PsiClassType filterOutTrashTypes(PsiType type) {
    if (!(type instanceof PsiClassType)) return null;
    if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return null;
    if (type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) return null;
    if (TypesUtil.resolvesTo(type, CommonClassNames.JAVA_UTIL_MAP)) return null;
    if (TypesUtil.resolvesTo(type, CommonClassNames.JAVA_UTIL_HASH_MAP)) return null;
    if (TypesUtil.resolvesTo(type, CommonClassNames.JAVA_UTIL_LIST)) return null;
    final PsiType erased = TypeConversionUtil.erasure(type);
    if (erased == null || erased.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return null;
    return (PsiClassType)type;
  }

  @NotNull
  public GrExpression[] getCallArguments() {
    final GrListOrMap literal = getElement();
    if (literal.isMap()) {
      final GrNamedArgument argument = literal.findNamedArgument("super");
      if (argument != null) {
        final GrExpression expression = argument.getExpression();
        if (expression instanceof GrListOrMap && !((GrListOrMap)expression).isMap()) {
          return ((GrListOrMap)expression).getInitializers();
        }
        if (expression != null) {
          return new GrExpression[]{expression};
        }

        return GrExpression.EMPTY_ARRAY;
      }
      else {
        return new GrExpression[]{literal};
      }
    }
    return literal.getInitializers();
  }

  @NotNull
  private PsiType[] getCallArgumentTypes() {
    final GrExpression[] arguments = getCallArguments();
    return ContainerUtil.map2Array(arguments, PsiType.class, new NullableFunction<GrExpression, PsiType>() {
      @Override
      public PsiType fun(GrExpression grExpression) {
        return grExpression.getType();
      }
    });
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    final PsiClassType.ClassResolveResult classResolveResult = myExpectedType.resolveGenerics();

    final GroovyResolveResult[] constructorCandidates =
      PsiUtil.getConstructorCandidates(myExpectedType, getCallArgumentTypes(), getElement());

    if (constructorCandidates.length == 0 && classResolveResult.getElement() != null) {
      return new GroovyResolveResult[]{new GroovyResolveResultImpl(classResolveResult)};
    }
    return constructorCandidates;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }
}
