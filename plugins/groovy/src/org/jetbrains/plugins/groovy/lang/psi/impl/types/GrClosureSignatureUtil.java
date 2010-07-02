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
import com.intellij.util.Function;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureSignature;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Maxim.Medvedev
 */
public class GrClosureSignatureUtil {
  private GrClosureSignatureUtil() {
  }


  @Nullable
  public static GrClosureSignature createSignature(GrCall call) {
    if (call instanceof GrMethodCall) {
      final GrExpression invokedExpression = ((GrMethodCall)call).getInvokedExpression();
      return getSignatureByInvokedExpression(invokedExpression);
    }

    if (call instanceof GrConstructorCall) {
      final GroovyResolveResult resolveResult = ((GrConstructorCall)call).resolveConstructorGenerics();
      final PsiElement element = resolveResult.getElement();
      if (element instanceof PsiMethod) {
        return createSignature(((PsiMethod)element), resolveResult.getSubstitutor());
      }
    }
    return null;
  }

  @Nullable
  private static GrClosureSignature getSignatureByInvokedExpression(GrExpression invokedExpression) {
    final PsiType type = invokedExpression.getType();
    if (type instanceof GrClosureType) return ((GrClosureType)type).getSignature();

    if (invokedExpression instanceof GrReferenceExpression) {
      final GroovyResolveResult resolveResult = ((GrReferenceExpression)invokedExpression).advancedResolve();
      final PsiElement element = resolveResult.getElement();
      if (element instanceof PsiMethod) {
        return createSignature((PsiMethod)element, resolveResult.getSubstitutor());
      }
    }
    return null;
  }

  public static GrClosureSignature createSignature(final GrClosableBlock block) {
    return new GrClosureSignatureImpl(block.getAllParameters(), null) {
      @Override
      public PsiType getReturnType() {
        return block.getReturnType();
      }
    };
  }

  public static GrClosureSignature createSignature(final PsiMethod method, PsiSubstitutor substitutor) {
    return new GrClosureSignatureImpl(method.getParameterList().getParameters(), null, substitutor) {
      @Override
      public PsiType getReturnType() {
        return PsiUtil.getSmartReturnType(method);
      }
    };
  }

  public static GrClosureSignature createSignature(PsiParameter[] parameters, PsiType returnType) {
    return new GrClosureSignatureImpl(parameters, returnType);
  }

  public static boolean isSignatureApplicable(GrClosureSignature signature, PsiType[] args, GroovyPsiElement context) {
    if (mapParametersToArguments(signature, args, (Function<PsiType, PsiType>)Function.ID, context) != null) return true;

    if (args.length == 1) {
      final GrClosureParameter[] parameters = signature.getParameters();
      if (parameters.length == 1 && parameters[0].getType() instanceof PsiArrayType) return false; 
      PsiType arg = args[0];
      if (arg instanceof GrTupleType) {
        args = ((GrTupleType)arg).getComponentTypes();
        if (mapParametersToArguments(signature, args, (Function<PsiType, PsiType>)Function.ID, context) != null) return true;
      }
    }
    return false;
  }

  @Nullable
  private static <Arg> ArgInfo<Arg>[] mapParametersToArguments(GrClosureSignature signature,
                                                               Arg[] args,
                                                               Function<Arg, PsiType> typeComputer,
                                                               GroovyPsiElement context) {
    GrClosureParameter[] params = signature.getParameters();
    if (args.length > params.length && !signature.isVarargs()) return null;
    int optional = getOptionalParamCount(signature, false);
    int notOptional = params.length - optional;
    if (signature.isVarargs()) notOptional--;
    if (notOptional > args.length) return null;

    final ArgInfo<Arg>[] map = mapSimple(params, args, typeComputer, context);
    if (map != null) return map;

    if (signature.isVarargs()) {
      return new ParameterMapperForVararg<Arg>(context, params, args, typeComputer).isApplicable();
    }
    return null;
  }

  @Nullable
  private static <Arg> ArgInfo<Arg>[] mapSimple(GrClosureParameter[] params,
                                                Arg[] args,
                                                Function<Arg, PsiType> typeComputer,
                                                GroovyPsiElement context) {
    ArgInfo<Arg>[] map = new ArgInfo[params.length];
    int optional = getOptionalParamCount(params, false);
    int notOptional = params.length - optional;
    int optionalArgs = args.length - notOptional;
    int cur = 0;
    for (int i = 0; i < args.length; i++, cur++) {
      while (optionalArgs == 0 && cur < params.length && params[cur].isOptional()) {
        cur++;
      }
      if (cur == params.length) return null;
      if (params[cur].isOptional()) optionalArgs--;
      if (!TypesUtil.isAssignable(params[cur].getType(), typeComputer.fun(args[i]), context, false)) return null;
      map[cur] = new ArgInfo<Arg>(args[i]);
    }
    for (int i = 0; i < map.length; i++) {
      if (map[i] == null) map[i] = new ArgInfo<Arg>(Collections.<Arg>emptyList(), false);
    }
    return map;
  }

  private static class ParameterMapperForVararg<Arg> {
    private GroovyPsiElement context;
    GrClosureParameter[] params;
    Arg[] args;
    PsiType[] types;
    PsiType vararg;
    private int paramLength;
    private ArgInfo<Arg>[] map;

    private ParameterMapperForVararg(GroovyPsiElement context,
                                     GrClosureParameter[] params,
                                     Arg[] args,
                                     Function<Arg, PsiType> typeComputer) {
      this.context = context;
      this.params = params;
      this.args = args;
      this.types = new PsiType[args.length];
      for (int i = 0; i < args.length; i++) {
        types[i] = typeComputer.fun(args[i]);
      }
      paramLength = params.length - 1;
      vararg = ((PsiArrayType)params[paramLength].getType()).getComponentType();
      map = new ArgInfo[params.length];
    }

    @Nullable
    public ArgInfo<Arg>[] isApplicable() {
      int notOptionals = 0;
      for (int i = 0; i < paramLength; i++) {
        if (!params[i].isOptional()) notOptionals++;
      }
      if (isApplicableInternal(0, 0, false, notOptionals)) {
        for (int i = 0; i < map.length; i++) {
          if (map[i] == null) map[i] = new ArgInfo<Arg>(false);
        }
        return map;
      }
      else {
        return null;
      }
    }

    private boolean isApplicableInternal(int curParam, int curArg, boolean skipOptionals, int notOptional) {
      int startParam = curParam;
      if (notOptional > args.length - curArg) return false;
      if (notOptional == args.length - curArg) skipOptionals = true;

      while (curArg < args.length) {
        if (skipOptionals) {
          while (curParam < paramLength && params[curParam].isOptional()) curParam++;
        }

        if (curParam == paramLength) break;

        if (params[curParam].isOptional()) {
          if (TypesUtil.isAssignable(params[curParam].getType(), types[curArg], context, false) &&
              isApplicableInternal(curParam + 1, curArg + 1, false, notOptional)) {
            map[curParam] = new ArgInfo<Arg>(args[curArg]);
            return true;
          }
          skipOptionals = true;
        }
        else {
          if (!TypesUtil.isAssignable(params[curParam].getType(), types[curArg], context, false)) {
            for (int i = startParam; i < curParam; i++) map[i] = null;
            return false;
          }
          map[curParam] = new ArgInfo<Arg>(args[curArg]);
          notOptional--;
          curArg++;
          curParam++;
        }
      }

      List<Arg> varargs = new ArrayList<Arg>();
      for (; curArg < args.length; curArg++) {
        if (!TypesUtil.isAssignable(vararg, types[curArg], context, false)) {
          for (int i = startParam; i < curParam; i++) map[i] = null;
          return false;
        }
        varargs.add(args[curArg]);
      }
      map[paramLength] = new ArgInfo<Arg>(varargs, true);
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

  public static class ArgInfo<ArgType> {
    public static final ArgInfo[] EMPTY_ARRAY = new ArgInfo[0];
    
    public List<ArgType> args;
    public final boolean isMultiArg;

    public ArgInfo(List<ArgType> args, boolean multiArg) {
      this.args = args;
      isMultiArg = multiArg;
    }

    public ArgInfo(ArgType arg) {
      this.args = Collections.singletonList(arg);
      this.isMultiArg = false;
    }

    public ArgInfo(boolean isMultiArg) {
      this.args = Collections.emptyList();
      this.isMultiArg = isMultiArg;
    }

    public static <ArgType> ArgInfo<ArgType>[] empty_array() {return EMPTY_ARRAY;}
  }
  /**
   * Returns array of lists which contain psiElements mapped to parameters
   *
   * @param signature
   * @param list
   * @return null if signature can not be applied to this argumentList
   */
  @Nullable
  public static ArgInfo<PsiElement>[] mapParametersToArguments(@NotNull GrClosureSignature signature,
                                                            @NotNull GrArgumentList list,
                                                            PsiManager manager,
                                                            GlobalSearchScope scope) {
    return mapParametersToArguments(signature, list, GrClosableBlock.EMPTY_ARRAY, manager, scope);
  }

  private static class InnerArg {
      List<PsiElement> list;
      PsiType type;

      InnerArg(PsiType type, PsiElement... elements) {
        this.list = new ArrayList<PsiElement>(Arrays.asList(elements));
        this.type = type;
      }
    }

  @Nullable
  public static ArgInfo<PsiElement>[] mapParametersToArguments(@NotNull GrClosureSignature signature,
                                                   @NotNull GrArgumentList list,
                                                   @NotNull GrClosableBlock[] closureArguments,
                                                   PsiManager manager,
                                                   GlobalSearchScope scope) {
    final GrNamedArgument[] namedArgs = list.getNamedArguments();
    boolean hasNamedArgs = namedArgs.length > 0;
    GrClosureParameter[] params = signature.getParameters();

    List<InnerArg> innerArgs = new ArrayList<InnerArg>();

    if (hasNamedArgs) {
      if (params.length == 0) return null;
      PsiType type = params[0].getType();
      if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP)) {
        innerArgs.add(new InnerArg(PsiUtil.createMapType(manager, scope), namedArgs));
      }
      else {
        return null;
      }
    }

    for (GrExpression expression : list.getExpressionArguments()) {
      innerArgs.add(new InnerArg(expression.getType(), expression));
    }

    for (GrClosableBlock closureArgument : closureArguments) {
      innerArgs.add(new InnerArg(closureArgument.getType(), closureArgument));
    }

    final ArgInfo<InnerArg>[] innerMap =
      mapParametersToArguments(signature, innerArgs.toArray(new InnerArg[innerArgs.size()]), new Function<InnerArg, PsiType>() {
        @Override
        public PsiType fun(InnerArg o) {
          return o.type;
        }
      }, list);
    if (innerMap == null) return null;
    
    ArgInfo<PsiElement>[] map = new ArgInfo[innerMap.length];
    int i = 0;
    if (hasNamedArgs) {
      map[i] = new ArgInfo<PsiElement>(innerMap[i].args.iterator().next().list, true);
      i++;
    }

    for (; i < innerMap.length; i++) {
      final ArgInfo<InnerArg> innerArg = innerMap[i];
      List<PsiElement> argList = new ArrayList<PsiElement>();
      for (InnerArg arg : innerArg.args) {
        argList.addAll(arg.list);
      }
      boolean multiArg = innerArg.isMultiArg || argList.size() > 1;
      map[i] = new ArgInfo<PsiElement>(argList, multiArg);
    }

    return map;
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
