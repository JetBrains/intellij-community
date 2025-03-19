// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * @author Medvedev Max
 */
public class TypeProvider {
  private final Map<GrMethod, PsiType[]> inferredTypes = new HashMap<>();

  public TypeProvider() {
  }

  public @NotNull PsiType getReturnType(@NotNull PsiMethod method) {
    if (method instanceof GrMethod) {
      GrTypeElement typeElement = ((GrMethod)method).getReturnTypeElementGroovy();
      if (typeElement != null) return typeElement.getType();
    }
    final PsiType smartReturnType = PsiUtil.getSmartReturnType(method);
    if (smartReturnType != null && !PsiTypes.nullType().equals(smartReturnType)) return smartReturnType;

    if (PsiTypes.nullType().equals(smartReturnType) && PsiUtil.isVoidMethod(method)) return PsiTypes.voidType();

    //todo make smarter. search for usages and infer type from them
    return TypesUtil.getJavaLangObject(method);
  }

  public @NotNull PsiType getVarType(@NotNull PsiVariable variable) {
    if (variable instanceof PsiParameter) return getParameterType((PsiParameter)variable);
    return getVariableTypeInner(variable);
  }

  private static @NotNull PsiType getVariableTypeInner(@NotNull PsiVariable variable) {
    PsiType type = null;
    if (variable instanceof GrVariable) {
      type = ((GrVariable)variable).getDeclaredType();
      if (type == null) {
        type = ((GrVariable)variable).getTypeGroovy();
      }
    }
    if (type == null) {
      type = variable.getType();
    }
    return type;
  }

  public @NotNull PsiType getParameterType(@NotNull PsiParameter parameter) {
    if (!(parameter instanceof GrParameter)) {
      PsiElement scope = parameter.getDeclarationScope();
      if (scope instanceof GrAccessorMethod) {
        return getVarType(((GrAccessorMethod)scope).getProperty());
      }
      return parameter.getType();
    }

    PsiElement parent = parameter.getParent();
    if (!(parent instanceof GrParameterList)) {
      return getVariableTypeInner(parameter);
    }

    PsiElement pparent = parent.getParent();
    if (!(pparent instanceof GrMethod)) return parameter.getType();

    PsiType[] types = inferMethodParameters((GrMethod)pparent);
    return types[((GrParameterList)parent).getParameterNumber((GrParameter)parameter)];
  }

  private PsiType @NotNull [] inferMethodParameters(@NotNull GrMethod method) {
    PsiType[] psiTypes = inferredTypes.get(method);
    if (psiTypes != null) return psiTypes;

    final GrParameter[] parameters = method.getParameters();

    final IntList paramInds = new IntArrayList(parameters.length);
    final PsiType[] types = PsiType.createArray(parameters.length);
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i].getTypeElementGroovy() == null) {
        paramInds.add(i);
      } else {
        types[i] = parameters[i].getType();
      }
    }

    if (!paramInds.isEmpty()) {
      final GrSignature signature = GrClosureSignatureUtil.createSignature(method, PsiSubstitutor.EMPTY);
      MethodReferencesSearch.search(method, true).forEach(psiReference -> {
        final PsiElement element = psiReference.getElement();
        final PsiManager manager = element.getManager();
        final GlobalSearchScope resolveScope = element.getResolveScope();

        if (element instanceof GrReferenceExpression) {
          final GrCall call = (GrCall)element.getParent();
          final GrClosureSignatureUtil.ArgInfo<PsiElement>[] argInfos = GrClosureSignatureUtil.mapParametersToArguments(signature, call);

          if (argInfos == null) return true;
          paramInds.forEach((IntConsumer)i -> {
            PsiType type = GrClosureSignatureUtil.getTypeByArg(argInfos[i], manager, resolveScope);
            types[i] = TypesUtil.getLeastUpperBoundNullable(type, types[i], manager);
          });
        }
        return true;
      });
    }
    paramInds.forEach((IntConsumer)i -> {
      if (types[i] == null || types[i] == PsiTypes.nullType()) {
        types[i] = parameters[i].getType();
      }
    });
    inferredTypes.put(method, types);
    return types;
  }

  public @NotNull PsiType getReturnType(GrClosableBlock closure) {
    final PsiType returnType = closure.getReturnType();
    if (PsiTypes.nullType().equals(returnType) && PsiUtil.isBlockReturnVoid(closure)) {
      return PsiTypes.voidType();
    }

    if (returnType == null) {
      return TypesUtil.getJavaLangObject(closure);
    }

    return TypesUtil.boxPrimitiveType(returnType, closure.getManager(), closure.getResolveScope());
  }
}
