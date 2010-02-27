/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.types.GrClosureSignatureImpl;

/**
 * @author Maxim.Medvedev
 */
public class GrClosureSignatureUtil {
  private GrClosureSignatureUtil() {
  }

  public static GrClosureSignature createSignature(PsiMethod method) {
    return new GrClosureSignatureImpl(method);
  }

  public static GrClosureSignature createSignature(GrClosableBlock block) {
    return new GrClosureSignatureImpl(block);
  }

  public static GrClosureSignature createSignature(PsiMethod method, PsiSubstitutor substitutor) {
    return new GrClosureSignatureImpl(method, substitutor);
  }

  public static GrClosureSignature createSignature(PsiParameter[] parameters, PsiType returnType) {
    return new GrClosureSignatureImpl(parameters, returnType);
  }

  public static boolean isSignatureApplicable(GrClosureSignature signature, PsiType[] args, PsiManager manager, GlobalSearchScope scope) {
    GrClosureParameter[] params = signature.getParameters();
    if (args.length > params.length && !signature.isVarargs()) return false;
    int optional = getOptionalParamCount(signature);
    int notOptional = params.length - optional;
    if (signature.isVarargs()) notOptional--;
    if (notOptional > args.length) return false;

    if (signature.isVarargs()) {
      if (isApplicable(params, args, params.length - 1, args.length, manager, scope)) return true;

      PsiType lastType = params[params.length - 1].getType();
      assert lastType instanceof PsiArrayType;
      PsiType varargType = ((PsiArrayType)lastType).getComponentType();

      for (int argCount = args.length - 1; argCount >= notOptional; argCount--) {
        if (!isApplicable(params, args, params.length - 1, argCount, manager, scope)) continue;
        if (!TypesUtil.isAssignableByMethodCallConversion(varargType, args[argCount], manager, scope)) continue;
        return true;
      }
      return false;
    }
    else {
      return isApplicable(params, args, params.length, args.length, manager, scope);
    }
  }

  private static boolean isApplicable(GrClosureParameter[] params,
                                      PsiType[] args,
                                      int paramCount,
                                      int argCount,
                                      PsiManager manager,
                                      GlobalSearchScope scope) {
    int optional = getOptionalParamCount(params);
    int notOptional = paramCount - optional;
    int optionalArgs = argCount - notOptional;
    int cur = 0;
    for (int i = 0; i < argCount; i++, cur++) {
      while (optionalArgs == 0 && cur < paramCount && params[cur].isOptional()) {
        cur++;
      }
      if (cur == paramCount) return false;
      if (params[cur].isOptional()) optionalArgs--;
      if (!TypesUtil.isAssignableByMethodCallConversion(params[cur].getType(), args[i], manager, scope)) return false;
    }
    return true;
  }


  public static int getOptionalParamCount(GrClosureSignature signature) {
    return getOptionalParamCount(signature.getParameters());
  }

  public static int getOptionalParamCount(GrClosureParameter[] parameters) {

    int count = 0;
    for (GrClosureParameter parameter : parameters) {
      if (parameter.isOptional()) count++;
    }
    return count;
  }
}
