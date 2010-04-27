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
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.util.*;

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
    int optional = getOptionalParamCount(signature, false);
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
          while (curParam < paramLength && params[curParam].isOptional()) curParam++;
        }

        if (curParam == paramLength) break;

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

  /**
   * Returns array of lists which contain psiElements mapped to parameters
   *
   * @param signature
   * @param list
   * @return null if signature can not be applied to this argumentList
   */
  @Nullable
  public static List<PsiElement>[] mapParametersToArguments(@NotNull GrClosureSignature signature,
                                                            @NotNull GrArgumentList list,
                                                            PsiManager manager,
                                                            GlobalSearchScope scope) {

    List<PsiElement>[] map = map(signature, list, manager, scope);
    if (map != null) return map;

    if (signature.isVarargs()) {
      return new ApplicabilityMapperForVararg(manager, scope, list, signature).map();
    }
    return null;
  }

  @Nullable
  private static List<PsiElement>[] map(@NotNull GrClosureSignature signature, @NotNull GrArgumentList list, PsiManager manager, GlobalSearchScope scope) {
    final GrExpression[] args = list.getExpressionArguments();
    final GrNamedArgument[] namedArgs = list.getNamedArguments();
    boolean hasNamedArgs = namedArgs.length > 0;


    GrClosureParameter[] params = signature.getParameters();

    List<PsiElement>[] map = new List[params.length];

    int paramLength = params.length;
    if (hasNamedArgs) {
      if (paramLength == 0) return null;
      PsiType type = params[0].getType();
      if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
        paramLength--;
        map[0] = Arrays.<PsiElement>asList(namedArgs);
      }
      else {
        return null;
      }
    }

    if (args.length > paramLength && !signature.isVarargs()) return null;
    int optional = getOptionalParamCount(signature, hasNamedArgs);
    int notOptional = paramLength - optional;
    if (signature.isVarargs()) notOptional--;
    if (notOptional > args.length) return null;

    int curParam = 0;
    optional = args.length - notOptional;
    if (hasNamedArgs) curParam++;
    for (int curArg = 0; curArg < args.length; curArg++, curParam++) {
      while (optional == 0 && curParam < paramLength && params[curParam].isOptional()) {
        curParam++;
      }
      if (curParam == paramLength) return null;
      if (params[curParam].isOptional()) optional--;
      if (TypesUtil.isAssignableByMethodCallConversion(params[curParam].getType(), args[curArg].getType(), manager, scope)) {
        map[curParam] = Collections.<PsiElement>singletonList(args[curArg]);
      }
      else {
        return null;
      }
    }
    return map;
  }

  private static class ApplicabilityMapperForVararg {
    private PsiManager manager;
    private GlobalSearchScope scope;
    private GrExpression[] args;
    private PsiType[] types;
    private GrNamedArgument[] namedArgs;
    private int paramLength;
    private GrClosureParameter[] params;
    private PsiType vararg;

    public ApplicabilityMapperForVararg(PsiManager manager, GlobalSearchScope scope, GrArgumentList list, GrClosureSignature signature) {
      this.manager = manager;
      this.scope = scope;
      args = list.getExpressionArguments();
      namedArgs = list.getNamedArguments();
      params = signature.getParameters();
      paramLength = params.length - 1;
      vararg = ((PsiArrayType)params[paramLength].getType()).getComponentType();

      types = new PsiType[args.length];
      for (int i = 0; i < args.length; i++) {
        types[i] = args[i].getType();
      }
    }

    @Nullable
    public List<PsiElement>[] map() {
      boolean hasNamedArgs = namedArgs.length > 0;
      List<PsiElement>[] map = new List[params.length];
      if (hasNamedArgs) {
        if (params.length == 0) return null;
        PsiType type = params[0].getType();
        if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
          map[0] = Arrays.<PsiElement>asList(namedArgs);
        }
        else {
          return null;
        }
      }
      int start = hasNamedArgs ? 1 : 0;
      int notOptionals = 0;
      for (int i = start; i < paramLength; i++) {
        if (!params[i].isOptional()) notOptionals++;
      }
      return mapInternal(start, 0, false, notOptionals, map);
    }

    @Nullable
    private List<PsiElement>[] mapInternal(int curParam, int curArg, boolean skipOptionals, int notOptional, List<PsiElement>[] map) {
      if (notOptional > args.length - curArg) return null;
      if (notOptional == args.length - curArg) skipOptionals = true;

      while (curArg < args.length) {
        if (skipOptionals) {
          while (curParam < paramLength && params[curParam].isOptional()) curParam++;
        }

        if (curParam == paramLength) break;

        if (params[curParam].isOptional()) {
          if (TypesUtil.isAssignable(params[curParam].getType(), types[curArg], manager, scope)) {
            List<PsiElement>[] copy = mapInternal(curParam + 1, curArg + 1, false, notOptional, copyMap(map));
            if (copy != null) return copy;
          }
          skipOptionals = true;
        }
        else {
          if (!TypesUtil.isAssignableByMethodCallConversion(params[curParam].getType(), types[curArg], manager, scope)) return null;
          map[curParam] = Collections.<PsiElement>singletonList(args[curArg]);
          notOptional--;
          curArg++;
          curParam++;
        }
      }

      map[paramLength] = new ArrayList<PsiElement>(args.length - curArg);

      for (; curArg < args.length; curArg++) {
        if (!TypesUtil.isAssignableByMethodCallConversion(vararg, types[curArg], manager, scope)) return null;
        map[paramLength].add(args[curArg]);
      }
      return map;
    }

    private static List<PsiElement>[] copyMap(List<PsiElement>[] map) {
      List<PsiElement>[] copy = new List[map.length];
      System.arraycopy(map, 0, copy, 0, map.length);
      return copy;
    }
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

  public static MultiMap<MethodSignature, PsiMethod> findMethodSignatures(PsiMethod[] methods) {
    List<Pair<MethodSignature, PsiMethod>> signatures = new ArrayList<Pair<MethodSignature, PsiMethod>>();
    for (PsiMethod method : methods) {
      List<MethodSignature> current;
      if (method instanceof GrMethod) {
        current = generateAllSignaturesForMethod((GrMethod)method, PsiSubstitutor.EMPTY);
      }
      else {
        current = Collections.singletonList(method.getSignature(PsiSubstitutor.EMPTY));
      }
      for (MethodSignature signature : current) {
        signatures.add(new Pair<MethodSignature, PsiMethod>(signature, method));
      }
    }

    MultiMap<MethodSignature, PsiMethod> map = new MultiMap<MethodSignature, PsiMethod>();
    for (Pair<MethodSignature, PsiMethod> pair : signatures) {
      map.putValue(pair.first, pair.second);
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
