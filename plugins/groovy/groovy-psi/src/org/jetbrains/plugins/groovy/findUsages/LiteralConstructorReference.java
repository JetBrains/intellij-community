/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils;
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner;
import org.jetbrains.plugins.groovy.lang.psi.api.EmptyGroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author peter
 */
public class LiteralConstructorReference extends PsiReferenceBase.Poly<GrListOrMap> {

  public LiteralConstructorReference(@NotNull GrListOrMap element) {
    super(element, TextRange.from(0, 0), false);
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return getElement();
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return getElement();
  }

  @Nullable
  public PsiClassType getConstructedClassType() {
    return CachedValuesManager.getCachedValue(getElement(), () -> CachedValueProvider.Result
      .create(inferConversionType(), PsiModificationTracker.MODIFICATION_COUNT));
  }

  @Nullable
  private PsiClassType inferConversionType() {
    final GrListOrMap listOrMap = getElement();
    final PsiClassType conversionType = getTargetConversionType(listOrMap);
    if (conversionType == null) return null;
    if (listOrMap.isEmpty()) {
      PsiType unboxed = TypesUtil.unboxPrimitiveTypeWrapper(conversionType);
      if (PsiType.BOOLEAN.equals(unboxed) || PsiType.CHAR.equals(unboxed)) {
        return null;
      }
    }

    final PsiType type = listOrMap.getType();
    final PsiType ownType = type instanceof PsiClassType ? ((PsiClassType)type).rawType() : type;
    if (ownType != null && TypesUtil.isAssignableWithoutConversions(conversionType.rawType(), ownType, listOrMap)) return null;

    final PsiClass resolved = conversionType.resolve();
    if (resolved != null) {
      if (InheritanceUtil.isInheritor(resolved, CommonClassNames.JAVA_UTIL_SET)) return null;
      if (InheritanceUtil.isInheritor(resolved, CommonClassNames.JAVA_UTIL_LIST)) return null;
    }
    return conversionType;
  }


  @Nullable
  public static PsiClassType getTargetConversionType(@NotNull final GrExpression expression) {
    //todo hack
    final PsiElement parent = PsiUtil.skipParentheses(expression.getParent(), true);

    PsiType type = null;
    if (parent instanceof GrSafeCastExpression) {
      type = ((GrSafeCastExpression)parent).getType();
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
          if (PsiType.BOOLEAN.equals(TypesUtil.unboxPrimitiveTypeWrapper(type))
              || TypesUtil.isEnum(type)
              || PsiUtil.isCompileStatic(expression)
              || TypesUtil.isClassType(type, CommonClassNames.JAVA_LANG_STRING)
              || TypesUtil.isClassType(type, CommonClassNames.JAVA_LANG_CLASS)) {
            type = null;
          }
        }
      }
    }

    if (PsiType.BOOLEAN.equals(type)) {
      type = TypesUtil.boxPrimitiveType(type, expression.getManager(), expression.getResolveScope());
    }
    return filterOutTrashTypes(type);
  }

  @Nullable
  private static PsiClassType filterOutTrashTypes(PsiType type) {
    if (!(type instanceof PsiClassType)) return null;
    if (type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) return null;
    if (TypesUtil.resolvesTo(type, CommonClassNames.JAVA_LANG_CLASS)) return null;
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
    return ContainerUtil.map2Array(arguments, PsiType.class, (NullableFunction<GrExpression, PsiType>)grExpression -> grExpression.getType());
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    PsiClassType type = getConstructedClassType();
    if (type == null) return GroovyResolveResult.EMPTY_ARRAY;

    final PsiClassType.ClassResolveResult classResolveResult = type.resolveGenerics();

    final GroovyResolveResult[] constructorCandidates = PsiUtil.getConstructorCandidates(type, getCallArgumentTypes(), getElement());

    if (constructorCandidates.length == 0) {
      final GroovyResolveResult result = GroovyResolveResultImpl.from(classResolveResult);
      if (result != EmptyGroovyResolveResult.INSTANCE) return new GroovyResolveResult[]{result};
    }
    return constructorCandidates;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }
}
