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
package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import gnu.trove.THashMap;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Medvedev
 */
public class GrClosureSignatureUtil {
  private GrClosureSignatureUtil() {
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

  public static boolean isSignatureApplicable(GrClosureSignature signature, PsiType[] args, GroovyPsiElement context) {
    if (isApplicable(signature, args, context)) return true;

    if (args.length == 1) {
      PsiType arg = args[0];
      if (arg instanceof GrTupleType) {
        args = ((GrTupleType)arg).getComponentTypes();
        if (isApplicable(signature, args, context)) return true;
      }
    }
    return false;
  }

  private static boolean isApplicable(GrClosureSignature signature, PsiType[] args, GroovyPsiElement context) {
    GrClosureParameter[] params = signature.getParameters();
    if (args.length > params.length && !signature.isVarargs()) return false;
    int optional = getOptionalParamCount(signature, false);
    int notOptional = params.length - optional;
    if (signature.isVarargs()) notOptional--;
    if (notOptional > args.length) return false;

    if (isApplicable(params, args, params.length, args.length, context)) {
      return true;
    }
    if (signature.isVarargs()) {
      return new ApplicabilityVerifierForVararg(context, params, args).isApplicable();
    }
    return false;
  }

  private static boolean isApplicable(GrClosureParameter[] params,
                                      PsiType[] args,
                                      int paramCount,
                                      int argCount,
                                      GroovyPsiElement context) {
    int optional = getOptionalParamCount(params, false);
    int notOptional = paramCount - optional;
    int optionalArgs = argCount - notOptional;
    int cur = 0;
    for (int i = 0; i < argCount; i++, cur++) {
      while (optionalArgs == 0 && cur < paramCount && params[cur].isOptional()) {
        cur++;
      }
      if (cur == paramCount) return false;
      if (params[cur].isOptional()) optionalArgs--;
      if (!TypesUtil.isAssignableByMethodCallConversion(params[cur].getType(), args[i], context)) return false;
    }
    return true;
  }

  private static class ApplicabilityVerifierForVararg {
    private GroovyPsiElement context;
    GrClosureParameter[] params;
    PsiType[] args;
    PsiType vararg;
    private int paramLength;

    private ApplicabilityVerifierForVararg(GroovyPsiElement context, GrClosureParameter[] params, PsiType[] args) {
      this.context = context;
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
          while (curParam < paramLength && params[curParam].isOptional()) curParam++;
        }

        if (curParam == paramLength) break;

        if (params[curParam].isOptional()) {
          if (TypesUtil.isAssignable(params[curParam].getType(), args[curArg], context) &&
              isApplicableInternal(curParam + 1, curArg + 1, false, notOptional)) {
            return true;
          }
          skipOptionals = true;
        }
        else {
          if (!TypesUtil.isAssignableByMethodCallConversion(params[curParam].getType(), args[curArg], context)) return false;
          notOptional--;
          curArg++;
          curParam++;
        }
      }

      for (; curArg < args.length; curArg++) {
        if (!TypesUtil.isAssignableByMethodCallConversion(vararg, args[curArg], context)) return false;
      }
      return true;
    }
  }


  public static int getOptionalParamCount(GrClosureSignature signature, boolean hasNamedArgs) {
    return getOptionalParamCount(signature.getParameters(), hasNamedArgs);
  }

  public static int getOptionalParamCount(GrClosureParameter[] parameters, boolean hasNamedArgs) {
    int count = 0;
    int i = 0;
    if (hasNamedArgs) i++;
    for (; i < parameters.length; i++) {
      GrClosureParameter parameter = parameters[i];
      if (parameter.isOptional()) count++;
    }
    return count;
  }

  public static List<MethodSignature> generateAllSignaturesForMethod(GrMethod method, PsiSubstitutor substitutor) {
    GrClosureSignature signature = createSignature(method, substitutor);
    String name = method.getName();
    GrClosureParameter[] params = signature.getParameters();
    PsiTypeParameter[] typeParameters = method.getTypeParameters();

    ArrayList<PsiType> newParams = new ArrayList<PsiType>(params.length);
    ArrayList<GrClosureParameter> opts = new ArrayList<GrClosureParameter>(params.length);
    ArrayList<Integer> optInds = new ArrayList<Integer>(params.length);

    for (int i = 0; i < params.length; i++) {
      if (params[i].isOptional()) {
        opts.add(params[i]);
        optInds.add(i);
      }
      else {
        newParams.add(params[i].getType());
      }
    }

    List<MethodSignature> result = new ArrayList<MethodSignature>(opts.size() + 1);
    result.add(generateSignature(name, newParams, typeParameters, substitutor));
    for (int i = 0; i < opts.size(); i++) {
      newParams.add(optInds.get(i), opts.get(i).getType());
      result.add(generateSignature(name, newParams, typeParameters, substitutor));
    }
    return result;
  }

  public static Map<MethodSignature, List<GrMethod>> findMethodSignatures(GrMethod[] methods) {
    List<Pair<MethodSignature, GrMethod>> signatures = new ArrayList<Pair<MethodSignature, GrMethod>>();
    for (GrMethod method : methods) {
      List<MethodSignature> current = generateAllSignaturesForMethod(method, PsiSubstitutor.EMPTY);
      for (MethodSignature signature : current) {
        signatures.add(new Pair<MethodSignature, GrMethod>(signature, method));
      }
    }

    THashMap<MethodSignature, List<GrMethod>> map = new THashMap<MethodSignature, List<GrMethod>>();
    for (Pair<MethodSignature, GrMethod> pair : signatures) {
      List<GrMethod> list = map.get(pair.first);
      if (list == null) {
        list = new ArrayList<GrMethod>();
        map.put(pair.first, list);
      }
      list.add(pair.second);
    }
    return map;
  }

  private static MethodSignature generateSignature(String name,
                                                   List<PsiType> paramTypes,
                                                   PsiTypeParameter[] typeParameters,
                                                   PsiSubstitutor substitutor) {
    return MethodSignatureUtil.createMethodSignature(name, paramTypes.toArray(new PsiType[paramTypes.size()]), typeParameters, substitutor);
  }
}
