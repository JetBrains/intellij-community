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
package org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import groovy.lang.Closure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;

/**
 * @author Max Medvedev
 */
public class GrDelegatesToUtil {
  @Nullable
  public static DelegatesToInfo getDelegatesToInfo(@NotNull PsiElement place, @NotNull final GrClosableBlock closableBlock) {
    GrCall call = getContainingCall(closableBlock);
    if (call == null) return null;

    GroovyResolveResult result = call.advancedResolve();

    if (GdkMethodUtil.isWithOrIdentity(result.getElement())) {
      final GrExpression qualifier = inferCallQualifier((GrMethodCall)call);
      if (qualifier == null) return null;

      return new DelegatesToInfo(qualifier.getType(), Closure.DELEGATE_FIRST);
    }

    GrClosureSignature signature = inferSignature(result.getElement());
    if (signature == null) return null;

    final GrClosureSignatureUtil.ArgInfo<PsiElement>[] map = mapArgs(place, call, signature);
    if (map == null) return null;

    final PsiParameter parameter = findParameter(closableBlock, map, result);
    if (parameter == null) return null;

    final PsiModifierList modifierList = parameter.getModifierList();
    if (modifierList == null) return null;

    final PsiAnnotation delegatesTo = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO);
    if (delegatesTo == null) return null;

    final PsiType type = inferDelegateType(delegatesTo, signature, map);
    if (type == null) return null;

    final int strategyValue = getStrategyValue(delegatesTo.findAttributeValue("strategy"));
    return new DelegatesToInfo(type, strategyValue);
  }

  private static GrClosureSignatureUtil.ArgInfo<PsiElement>[] mapArgs(PsiElement place, GrCall call, GrClosureSignature signature) {
    GrClosureSignature rawSignature = GrClosureSignatureUtil.rawSignature(signature);
    return GrClosureSignatureUtil.mapParametersToArguments(
      rawSignature, call.getNamedArguments(), call.getExpressionArguments(), call.getClosureArguments(), place, false, false
    );
  }

  @Nullable
  private static GrClosureSignature inferSignature(@Nullable PsiElement element) {
    if (element instanceof PsiMethod) {
      return GrClosureSignatureUtil.createSignature((PsiMethod)element, PsiSubstitutor.EMPTY);
    }
    else if (element instanceof GrVariable) {
      final PsiType type = ((GrVariable)element).getTypeGroovy();
      if (type instanceof GrClosureType) {
        final GrSignature signature = ((GrClosureType)type).getSignature();
        if (signature instanceof GrClosureSignature) {
          return (GrClosureSignature)signature;
        }
      }
    }
    return null;
  }

  @Nullable
  private static PsiParameter findParameter(@NotNull GrClosableBlock closableBlock,
                                            @NotNull GrClosureSignatureUtil.ArgInfo<PsiElement>[] map,
                                            @NotNull GroovyResolveResult result) {
    final PsiElement element = result.getElement();
    if (element instanceof PsiMethod) {
      final PsiParameter[] parameters = ((PsiMethod)element).getParameterList().getParameters();

      for (int i = 0; i < map.length; i++) {
        if (map[i].args.contains(closableBlock)) return parameters[i];
      }
    }

    return null;
  }

  @Nullable
  private static PsiType inferDelegateType(@NotNull PsiAnnotation delegatesTo,
                                           @NotNull GrClosureSignature signature,
                                           @NotNull GrClosureSignatureUtil.ArgInfo<PsiElement>[] map) {
    final PsiAnnotationMemberValue value = delegatesTo.findDeclaredAttributeValue("value");
    if (value instanceof GrReferenceExpression) {
      return extractTypeFromClassType(((GrReferenceExpression)value).getType());
    }
    else if (value instanceof PsiClassObjectAccessExpression) {
      return extractTypeFromClassType(((PsiClassObjectAccessExpression)value).getType());
    }
    else if (value == null ||
             value instanceof PsiLiteralExpression && ((PsiLiteralExpression)value).getType() == PsiType.NULL ||
             value instanceof GrLiteral && ((GrLiteral)value).getType() == PsiType.NULL) {
      String target = GrAnnotationUtil.inferStringAttribute(delegatesTo, "target");
      if (target == null) return null;

      final int parameter = findTargetParameter(delegatesTo, target);
      if (parameter >= 0) {
        final PsiType type = map[parameter].type;
        final Integer index = GrAnnotationUtil.inferIntegerAttribute(delegatesTo, "genericTypeIndex");
        if (index != null) {
          return inferGenericArgType(signature, type, index, parameter);
        }
        else {
          return type;
        }
      }
    }
    else if (value instanceof PsiExpression) {
      return ((PsiExpression)value).getType();
    }
    return null;
  }

  @Nullable
  private static PsiType inferGenericArgType(@NotNull GrClosureSignature signature,
                                             @NotNull PsiType targetType,
                                             int genericIndex,
                                             int param) {
    if (targetType instanceof PsiClassType) {
      final PsiClassType.ClassResolveResult result = ((PsiClassType)targetType).resolveGenerics();
      final PsiClass psiClass = result.getElement();
      if (psiClass != null) {
        final PsiSubstitutor substitutor = result.getSubstitutor();

        final PsiType baseType = signature.getParameters()[param].getType();
        final PsiClass baseClass = PsiUtil.resolveClassInClassTypeOnly(baseType);

        if (baseClass != null && InheritanceUtil.isInheritorOrSelf(psiClass, baseClass, true)) {
          final PsiTypeParameter[] typeParameters = baseClass.getTypeParameters();
          if (genericIndex < typeParameters.length) {
            final PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, psiClass, substitutor);
            return superClassSubstitutor.substitute(typeParameters[genericIndex]);
          }
        }
      }
    }
    return null;
  }

  private static int findTargetParameter(@NotNull PsiAnnotation delegatesTo, @NotNull String target) {
    //                                                 ann      mod.list    parameter   param.list
    final PsiParameterList list = (PsiParameterList)delegatesTo.getParent().getParent().getParent();

    PsiParameter[] parameters = list.getParameters();
    for (int i = 0; i < parameters.length; i++) {
      final PsiModifierList modifierList = parameters[i].getModifierList();
      if (modifierList == null) continue;

      final PsiAnnotation targetAnnotation = modifierList.findAnnotation(GroovyCommonClassNames.GROOVY_LANG_DELEGATES_TO_TARGET);
      if (targetAnnotation == null) continue;

      final String value = GrAnnotationUtil.inferStringAttribute(targetAnnotation, "value");
      if (value == null) continue;

      if (value.equals(target)) return i;
    }

    return -1;
  }

  @Nullable
  private static GrExpression inferCallQualifier(@NotNull GrMethodCall call) {
    final GrExpression expression = call.getInvokedExpression();
    if (!(expression instanceof GrReferenceExpression)) return null;
    return ((GrReferenceExpression)expression).getQualifier();
  }

  @Nullable
  private static PsiType extractTypeFromClassType(@Nullable PsiType type) {
    if (type instanceof PsiClassType) {
      final PsiClass resolved = ((PsiClassType)type).resolve();
      if (resolved != null && CommonClassNames.JAVA_LANG_CLASS.equals(resolved.getQualifiedName())) {
        final PsiType[] parameters = ((PsiClassType)type).getParameters();
        if (parameters.length == 1) {
          return parameters[0];
        }
      }
    }
    return null;
  }

  private static int getStrategyValue(@Nullable PsiAnnotationMemberValue strategy) {
    if (strategy == null) return -1;

    final String text = strategy.getText();
    if ("0".equals(text)) return 0;
    if ("1".equals(text)) return 1;
    if ("2".equals(text)) return 2;
    if ("3".equals(text)) return 3;
    if ("4".equals(text)) return 4;

    if (text.endsWith("OWNER_FIRST")) return Closure.OWNER_FIRST;
    if (text.endsWith("DELEGATE_FIRST")) return Closure.DELEGATE_FIRST;
    if (text.endsWith("OWNER_ONLY")) return Closure.OWNER_ONLY;
    if (text.endsWith("DELEGATE_ONLY")) return Closure.DELEGATE_ONLY;
    if (text.endsWith("TO_SELF")) return Closure.TO_SELF;

    return -1;
  }

  @Nullable
  static GrCall getContainingCall(@NotNull GrClosableBlock closableBlock) {
    final PsiElement parent = closableBlock.getParent();
    if (parent instanceof GrCall && ArrayUtil.contains(closableBlock, ((GrCall)parent).getClosureArguments())) {
      return (GrCall)parent;
    }
    else if (parent instanceof GrArgumentList) {
      final PsiElement parent1 = parent.getParent();
      if (parent1 instanceof GrCall) {
        return (GrCall)parent1;
      }
    }

    return null;
  }

  public static class DelegatesToInfo {
    final PsiType myClassToDelegate;
    final int myStrategy;

    private DelegatesToInfo(@Nullable PsiType classToDelegate, int strategy) {
      myClassToDelegate = classToDelegate;
      myStrategy = strategy;
    }

    public PsiType getTypeToDelegate() {
      return myClassToDelegate;
    }

    public int getStrategy() {
      return myStrategy;
    }
  }
}
