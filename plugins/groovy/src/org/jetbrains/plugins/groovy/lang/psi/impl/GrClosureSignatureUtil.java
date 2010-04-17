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
    if (isApplicable(signature, args, manager, scope)) return true;

    if (args.length == 1) {
      PsiType arg = args[0];
      if (arg instanceof GrTupleType) {
        args = ((GrTupleType)arg).getComponentTypes();
        if (isApplicable(signature, args, manager, scope)) return true;
      }
    }
    return false;
  }

  private static boolean isApplicable(GrClosureSignature signature, PsiType[] args, PsiManager manager, GlobalSearchScope scope) {
     GrClosureParameter[] params = signature.getParameters();
    if (args.length > params.length && !signature.isVarargs()) return false;
    int optional = getOptionalParamCount(signature);
    int notOptional = params.length - optional;
    if (signature.isVarargs()) notOptional--;
    if (notOptional > args.length) return false;

    if (isApplicable(params, args, params.length, args.length, manager, scope)) {
      return true;
    }
    if (signature.isVarargs()) {
      return new ApplicabilityVerifierForVararg(manager, scope, params, args).isApplicable();
    }
    return false;
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

  private static class ApplicabilityVerifierForVararg {
    private PsiManager manager;
    GlobalSearchScope scope;
    GrClosureParameter[] params;
    PsiType[] args;
    PsiType vararg;
    private int paramLength;

    private ApplicabilityVerifierForVararg(PsiManager manager, GlobalSearchScope scope, GrClosureParameter[] params, PsiType[] args) {
      this.manager = manager;
      this.scope = scope;
      this.params = params;
      this.args = args;
      paramLength = params.length - 1;
      vararg = ((PsiArrayType)params[paramLength].getType()).getComponentType();
    }

    public boolean isApplicable() {
      int notOptionals = 0;
      for (int i = 0; i < paramLength; i++) {
        if (!params[i].isOptional()) notOptionals++;
      }
      return isApplicableInternal(0, 0, false, notOptionals);
    }

    private boolean isApplicableInternal(int curParam, int curArg, boolean skipOptionals, int notOptional) {
      if (notOptional > args.length - curArg) return false;
      if (notOptional == args.length - curArg) skipOptionals = true;

      while (curArg < args.length) {
        if (skipOptionals) {
          while (params[curParam].isOptional()) curParam++;
        }

        if (curParam >= paramLength) break;

        if (params[curParam].isOptional()) {
          if (TypesUtil.isAssignable(params[curParam].getType(), args[curArg], manager, scope) &&
              isApplicableInternal(curParam + 1, curArg + 1, false, notOptional)) {
            return true;
          }
          skipOptionals = true;
        }
        else {
          if (!TypesUtil.isAssignableByMethodCallConversion(params[curParam].getType(), args[curArg], manager, scope)) return false;
          notOptional--;
          curArg++;
          curParam++;
        }
      }

      for (; curArg < args.length; curArg++) {
        if (!TypesUtil.isAssignableByMethodCallConversion(vararg, args[curArg], manager, scope)) return false;
      }
      return true;
    }
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
