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
package org.jetbrains.plugins.groovy.refactoring.convertToJava;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.containers.HashMap;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrClosureSignature;
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

import java.util.Map;

/**
 * @author Medvedev Max
 */
public class TypeProvider {
  private final Map<GrMethod, PsiType[]> inferredTypes = new HashMap<>();

  public TypeProvider() {
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  @NotNull
  public PsiType getReturnType(@NotNull PsiMethod method) {
    if (method instanceof GrMethod) {
      GrTypeElement typeElement = ((GrMethod)method).getReturnTypeElementGroovy();
      if (typeElement != null) return typeElement.getType();
    }
    final PsiType smartReturnType = PsiUtil.getSmartReturnType(method);
    if (smartReturnType != null && !PsiType.NULL.equals(smartReturnType)) return smartReturnType;

    if (PsiType.NULL.equals(smartReturnType) && PsiUtil.isVoidMethod(method)) return PsiType.VOID;

    //todo make smarter. search for usages and infer type from them
    return TypesUtil.getJavaLangObject(method);
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  @NotNull
  public PsiType getVarType(@NotNull PsiVariable variable) {
    if (variable instanceof PsiParameter) return getParameterType((PsiParameter)variable);
    return getVariableTypeInner(variable);
  }

  @NotNull
  private static PsiType getVariableTypeInner(@NotNull PsiVariable variable) {
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

  @NotNull
  public PsiType getParameterType(@NotNull PsiParameter parameter) {
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

  @NotNull
  private PsiType[] inferMethodParameters(@NotNull GrMethod method) {
    PsiType[] psiTypes = inferredTypes.get(method);
    if (psiTypes != null) return psiTypes;

    final GrParameter[] parameters = method.getParameters();

    final TIntArrayList paramInds = new TIntArrayList(parameters.length);
    final PsiType[] types = PsiType.createArray(parameters.length);
    for (int i = 0; i < parameters.length; i++) {
      if (parameters[i].getTypeElementGroovy() == null) {
        paramInds.add(i);
      } else {
        types[i] = parameters[i].getType();
      }
    }

    if (!paramInds.isEmpty()) {
      final GrClosureSignature signature = GrClosureSignatureUtil.createSignature(method, PsiSubstitutor.EMPTY);
      MethodReferencesSearch.search(method, true).forEach(psiReference -> {
        final PsiElement element = psiReference.getElement();
        final PsiManager manager = element.getManager();
        final GlobalSearchScope resolveScope = element.getResolveScope();

        if (element instanceof GrReferenceExpression) {
          final GrCall call = (GrCall)element.getParent();
          final GrClosureSignatureUtil.ArgInfo<PsiElement>[] argInfos = GrClosureSignatureUtil.mapParametersToArguments(signature, call);

          if (argInfos == null) return true;
          paramInds.forEach(new TIntProcedure() {
            @Override
            public boolean execute(int i) {
              PsiType type = GrClosureSignatureUtil.getTypeByArg(argInfos[i], manager, resolveScope);
              types[i] = TypesUtil.getLeastUpperBoundNullable(type, types[i], manager);
              return true;
            }
          });
        }
        return true;
      });
    }
    paramInds.forEach(new TIntProcedure() {
      @Override
      public boolean execute(int i) {
        if (types[i] == null || types[i] == PsiType.NULL) {
          types[i] = parameters[i].getType();
        }
        return true;
      }
    });
    inferredTypes.put(method, types);
    return types;
  }

  @NotNull
  public PsiType getReturnType(GrClosableBlock closure) {
    final PsiType returnType = closure.getReturnType();
    if (PsiType.NULL.equals(returnType) && PsiUtil.isBlockReturnVoid(closure)) {
      return PsiType.VOID;
    }

    if (returnType == null) {
      return TypesUtil.getJavaLangObject(closure);
    }

    return TypesUtil.boxPrimitiveType(returnType, closure.getManager(), closure.getResolveScope());
  }
}
