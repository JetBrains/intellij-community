// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.signatures;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Trinity;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.signatures.GrSignature;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClosureParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.impl.LazyFqnClassType;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.api.Applicability;
import org.jetbrains.plugins.groovy.lang.resolve.api.CallSignature;
import org.jetbrains.plugins.groovy.lang.resolve.impl.ArgumentsKt;
import org.jetbrains.plugins.groovy.lang.typing.GroovyClosureType;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createTypeByFQClassName;
import static org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.JAVA_UTIL_LINKED_HASH_MAP;

/**
 * @author Maxim.Medvedev
 */
public final class GrClosureSignatureUtil {
  private static final Logger LOG = Logger.getInstance(GrClosureSignatureUtil.class);

  private GrClosureSignatureUtil() {
  }

  @Nullable
  public static GrSignature createSignature(GrCall call) {
    if (call instanceof GrMethodCall) {
      final GrExpression invokedExpression = ((GrMethodCall)call).getInvokedExpression();
      final PsiType type = invokedExpression.getType();
      if (type instanceof GroovyClosureType) {
        Collection<CallSignature<?>> signatures = ((GroovyClosureType)type).applicableSignatures(ArgumentsKt.getArguments(call));
        if (signatures.size() == 1) {
          return new GrCallSignatureAdapter(ContainerUtil.getFirstItem(signatures));
        }
      }
    }

    final GroovyResolveResult resolveResult = call.advancedResolve();
    final PsiElement element = resolveResult.getElement();
    if (element instanceof PsiMethod) {
      return createSignature((PsiMethod)element, resolveResult.getSubstitutor());
    }

    return null;
  }

  @NotNull
  public static GrSignature createSignature(@NotNull MethodSignature signature) {
    final PsiType[] types = signature.getParameterTypes();
    GrClosureParameter[] parameters = ContainerUtil.map(types, type ->
      new GrImmediateClosureParameterImpl(type, null, false, null), new GrClosureParameter[types.length]
    );
    return new GrImmediateClosureSignatureImpl(parameters, null, false, false);
  }

  @NotNull
  public static GrSignature createSignature(@NotNull GrFunctionalExpression expression) {
    return new GrFunctionalExpressionSignature(expression);
  }

  @NotNull
  public static GrSignature createSignature(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor) {
    return createSignature(method, substitutor, false);
  }

  @NotNull
  public static GrSignature createSignature(@NotNull PsiMethod method, @NotNull PsiSubstitutor substitutor, boolean eraseParameterTypes) {
    return createSignature(method, substitutor, eraseParameterTypes, method);
  }

  @NotNull
  public static GrSignature createSignature(@NotNull PsiMethod method,
                                            @NotNull PsiSubstitutor substitutor,
                                            boolean types,
                                            @NotNull PsiElement place) {
    return new GrMethodSignatureImpl(method, substitutor, types, place);
  }

  @NotNull
  public static GrSignature createSignature(PsiParameter[] parameters, @Nullable PsiType returnType) {
    return new GrImmediateClosureSignatureImpl(parameters, returnType);
  }

  @Nullable
  public static PsiType getReturnType(@NotNull final List<? extends GrSignature> signatures, @NotNull GrMethodCall expr) {
    return getReturnType(signatures, PsiUtil.getArgumentTypes(expr.getInvokedExpression(), true), expr);
  }

  @Nullable
  public static PsiType getReturnType(@NotNull final List<? extends GrSignature> signatures, PsiType @Nullable [] args, @NotNull PsiElement context) {
    if (signatures.size() == 1) {
      return signatures.get(0).getReturnType();
    }

    final PsiManager manager = context.getManager();

    if (args == null) {
      return TypesUtil.getLeastUpperBoundNullable(ContainerUtil.map(signatures, GrSignature::getReturnType), manager);
    }

    final List<Trinity<GrSignature, ArgInfo<PsiType>[], Applicability>> results =
      getSignatureApplicabilities(signatures, args, context);

    if (results.size() == 1) return results.get(0).first.getReturnType();

    return TypesUtil.getLeastUpperBoundNullable(ContainerUtil.map(results, it -> it.first.getReturnType()), manager);
  }

  public static boolean isSignatureApplicable(@NotNull List<? extends GrSignature> signature, PsiType @NotNull [] args, @NotNull PsiElement context) {
    return isSignatureApplicableConcrete(signature, args, context) != Applicability.inapplicable;
  }

  public static Applicability isSignatureApplicableConcrete(@NotNull List<? extends GrSignature> signatures,
                                                            final PsiType @NotNull [] args,
                                                            @NotNull final PsiElement context) {
    final List<Trinity<GrSignature, ArgInfo<PsiType>[], Applicability>> results =
      getSignatureApplicabilities(signatures, args, context);
    if (results.isEmpty()) {
      return Applicability.inapplicable;
    }
    else if (results.size() == 1) {
      return results.get(0).third;
    }
    else {
      return Applicability.applicable;
    }
  }

  @Nullable
  public static Trinity<GrSignature, ArgInfo<PsiType>[], Applicability> getApplicableSignature(@NotNull List<? extends GrSignature> signatures,
                                                                                               final PsiType @Nullable [] args,
                                                                                               @NotNull final GroovyPsiElement context) {
    if (args == null) return null;
    final List<Trinity<GrSignature, ArgInfo<PsiType>[], Applicability>> results = getSignatureApplicabilities(signatures, args, context);

    if (results.size() == 1) return results.get(0);
    else return null;
  }

  private static List<Trinity<GrSignature, ArgInfo<PsiType>[], Applicability>> getSignatureApplicabilities(@NotNull List<? extends GrSignature> signatures,
                                                                                                           final PsiType @NotNull [] args,
                                                                                                           @NotNull final PsiElement context) {
    final List<Trinity<GrSignature, ArgInfo<PsiType>[], Applicability>> results = new ArrayList<>();
    for (GrSignature signature : signatures) {
      ArgInfo<PsiType>[] map = mapArgTypesToParameters(signature, args, context, false);
      if (map != null) {
        results.add(new Trinity<>(signature, map, isSignatureApplicableInner(map, signature)));
        continue;
      }

      // check for the case foo([1, 2, 3]) if foo(int, int, int)
      if (args.length == 1 && PsiUtil.isInMethodCallContext(context)) {
        final GrClosureParameter[] parameters = signature.getParameters();
        if (parameters.length == 1 && parameters[0].getType() instanceof PsiArrayType) {
          continue;
        }
        PsiType arg = args[0];
        if (arg instanceof GrTupleType) {
          PsiType[] _args = ((GrTupleType)arg).getComponentTypesArray();
          map = mapArgTypesToParameters(signature, _args, context, false);
          if (map != null) {
            results.add(new Trinity<>(signature, map,
                                      isSignatureApplicableInner(map,
                                                                 signature)));
          }
        }
      }
    }
    return results;
  }

  private static Applicability isSignatureApplicableInner(ArgInfo<PsiType> @NotNull [] infos, @NotNull GrSignature signature) {
    GrClosureParameter[] parameters = signature.getParameters();
    for (int i = 0; i < infos.length; i++) {
      ArgInfo<PsiType> info = infos[i];
      if (info.args.size() != 1 || info.isMultiArg) continue;
      PsiType type = info.args.get(0);
      if (type != null) continue;

      PsiType pType = parameters[i].getType();
      if (pType != null && !pType.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
        return Applicability.canBeApplicable;
      }
    }
    return Applicability.applicable;
  }

  @NotNull
  static List<GrSignature> curryImpl(@NotNull GrSignature original, PsiType[] args, int position, @NotNull PsiElement context) {
    GrClosureParameter[] params = original.getParameters();

    List<GrClosureParameter> newParams = new ArrayList<>(params.length);
    List<GrClosureParameter> opts = new ArrayList<>(params.length);
    List<Integer> optInds = new ArrayList<>(params.length);

    if (position == -1) {
      position = params.length - args.length;
    }

    if (position < 0 || position >= params.length) {
      return Collections.emptyList();
    }

    for (int i = 0; i < params.length; i++) {
      if (params[i].isOptional()) {
        opts.add(params[i]);
        optInds.add(i);
      }
      else {
        newParams.add(params[i]);
      }
    }

    final PsiType rtype = original.getReturnType();
    final ArrayList<GrSignature> result = new ArrayList<>();
    checkAndAddSignature(result, args, position, newParams, rtype, context);

    for (int i = 0; i < opts.size(); i++) {
      newParams.add(optInds.get(i), opts.get(i));
      checkAndAddSignature(result, args, position, newParams, rtype, context);
    }

    return result;
  }

  public static boolean isVarArgsImpl(GrClosureParameter @NotNull [] parameters) {
    return parameters.length > 0 && parameters[parameters.length - 1].getType() instanceof PsiArrayType;
  }

  public static ArgInfo<PsiType> @Nullable [] mapArgTypesToParameters(@NotNull GrSignature signature,
                                                                      PsiType @NotNull [] args,
                                                                      @NotNull PsiElement context,
                                                                      boolean partial) {
    return mapParametersToArguments(signature, args, FunctionUtil.id(), context, partial);
  }

  public static <Arg> ArgInfo<Arg> @Nullable [] mapParametersToArguments(@NotNull GrSignature signature,
                                                                         Arg @NotNull [] args,
                                                                         @NotNull Function<Arg, PsiType> typeComputer,
                                                                         @NotNull PsiElement context,
                                                                         boolean partial) {
    LOG.assertTrue(signature.isValid(), signature.getClass());

    if (checkForOnlyMapParam(signature, args.length)) return ArgInfo.empty_array();
    GrClosureParameter[] params = signature.getParameters();
    if (args.length > params.length && !signature.isVarargs() && !partial) return null;
    int optional = getOptionalParamCount(signature, context);
    int notOptional = params.length - optional;
    if (signature.isVarargs()) notOptional--;
    if (notOptional > args.length && !partial) return null;

    final ArgInfo<Arg>[] map = mapSimple(params, args, typeComputer, context, optional, false);
    if (map != null) return map;

    if (signature.isVarargs()) {
      return new ParameterMapperForVararg<>(context, params, args, typeComputer).isApplicable();
    }

    if (!partial) return null;

    return mapSimple(params, args, typeComputer, context, optional, true);
  }

  private static boolean checkForOnlyMapParam(@NotNull GrSignature signature, final int argCount) {
    if (argCount > 0 || signature.isCurried()) return false;
    final GrClosureParameter[] parameters = signature.getParameters();
    if (parameters.length != 1) return false;
    final PsiType type = parameters[0].getType();
    return InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP);
  }

  private static <Arg> ArgInfo<Arg> @Nullable [] mapSimple(GrClosureParameter @NotNull [] params,
                                                           Arg @NotNull [] args,
                                                           @NotNull Function<? super Arg, ? extends PsiType> typeComputer,
                                                           @NotNull PsiElement context,
                                                           int optional,
                                                           boolean partial) {
    if (args.length > params.length && !partial) return null;

    //noinspection unchecked
    ArgInfo<Arg>[] map = new ArgInfo[params.length];
    int notOptional = params.length - optional;
    int optionalArgs = args.length - notOptional;

    if (notOptional > args.length && !partial) return null;

    int cur = 0;
    for (int i = 0; i < args.length; i++, cur++) {
      while (optionalArgs == 0 && cur < params.length && params[cur].isOptional()) {
        cur++;
      }
      if (cur == params.length) return partial ? map : null;
      if (params[cur].isOptional()) optionalArgs--;
      final PsiType type = typeComputer.fun(args[i]);
      if (!isAssignableByConversion(params[cur].getType(), type, context)) return partial ? map : null;
      map[cur] = new ArgInfo<>(args[i], type);
    }
    for (int i = 0; i < map.length; i++) {
      if (map[i] == null) map[i] = ArgInfo.empty();
    }
    return map;
  }

  @Contract("null, _, _ -> true; _, null, _ -> true")
  public static boolean isAssignableByConversion(@Nullable PsiType paramType, @Nullable PsiType argType, @NotNull PsiElement context) {
    if (argType == null || paramType == null) {
      return true;
    }
    if (TypesUtil.isAssignableByMethodCallConversion(paramType, argType, context)) {
      return true;
    }

    final PsiType lType = TypesUtil.rawSecondGeneric(paramType, context.getProject());
    final PsiType rType = TypesUtil.rawSecondGeneric(argType, context.getProject());
    if (lType == null && rType == null) return false;

    return TypesUtil.isAssignableByMethodCallConversion(lType != null ? lType : paramType, rType != null ? rType : argType, context);
  }

  public static void checkAndAddSignature(List<? super GrSignature> list,
                                          PsiType[] args,
                                          int position,
                                          List<? extends GrClosureParameter> params,
                                          PsiType returnType,
                                          @NotNull PsiElement context) {
    final int last = position + args.length;
    if (last > params.size()) return;

    for (int i = position; i < last; i++) {
      final GrClosureParameter p = params.get(i);
      final PsiType type = p.getType();
      if (!isAssignableByConversion(type, args[i - position], context)) return;
    }
    GrClosureParameter[] _p = new GrClosureParameter[params.size() - args.length];
    int j = 0;
    for (int i = 0; i < position; i++) {
      _p[j++] = params.get(i);
    }
    for (int i = position + args.length; i < params.size(); i++) {
      _p[j++] = params.get(i);
    }

    list.add(new GrImmediateClosureSignatureImpl(_p, returnType, _p.length > 0 && _p[_p.length - 1].getType() instanceof PsiArrayType, true));
  }


  @Nullable
  public static GrSignature createSignature(GroovyResolveResult resolveResult) {
    final PsiElement resolved = resolveResult.getElement();
    if (!(resolved instanceof PsiMethod)) return null;
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    return createSignature((PsiMethod)resolved, substitutor);
  }

  private static class ParameterMapperForVararg<Arg> {
    private final PsiElement context;
    private final GrClosureParameter[] params;
    private final Arg[] args;
    private final PsiType[] types;
    private final PsiType vararg;
    private final int paramLength;
    private final ArgInfo<Arg>[] map;

    private ParameterMapperForVararg(PsiElement context,
                                     GrClosureParameter[] params,
                                     Arg[] args,
                                     Function<Arg, PsiType> typeComputer) {
      this.context = context;
      this.params = params;
      this.args = args;
      this.types = PsiType.createArray(args.length);
      for (int i = 0; i < args.length; i++) {
        types[i] = typeComputer.fun(args[i]);
      }
      paramLength = params.length - 1;
      final PsiType lastParamType = params[paramLength].getType();
      assert lastParamType instanceof PsiArrayType;
      vararg = ((PsiArrayType)lastParamType).getComponentType();
      //noinspection unchecked
      map = new ArgInfo[params.length];
    }

    private ArgInfo<Arg> @Nullable [] isApplicable() {
      int notOptionals = 0;
      for (int i = 0; i < paramLength; i++) {
        if (!params[i].isOptional()) notOptionals++;
      }
      if (isApplicableInternal(0, 0, notOptionals)) {
        for (int i = 0; i < map.length; i++) {
          if (map[i] == null) map[i] = ArgInfo.empty();
        }
        return map;
      }
      else {
        return null;
      }
    }

    private boolean isApplicableInternal(int curParam, int curArg, int notOptional) {
      boolean skipOptionals = false;
      int startParam = curParam;
      if (notOptional > args.length - curArg) return false;
      if (notOptional == args.length - curArg) skipOptionals = true;

      while (curArg < args.length) {
        if (skipOptionals) {
          while (curParam < paramLength && params[curParam].isOptional()) curParam++;
        }

        if (curParam == paramLength) break;

        if (params[curParam].isOptional()) {
          if (isAssignableByConversion(params[curParam].getType(), types[curArg], context) &&
              isApplicableInternal(curParam + 1, curArg + 1, notOptional)) {
            map[curParam] = new ArgInfo<>(args[curArg], types[curArg]);
            return true;
          }
          skipOptionals = true;
        }
        else {
          if (!isAssignableByConversion(params[curParam].getType(), types[curArg], context)) {
            for (int i = startParam; i < curParam; i++) map[i] = null;
            return false;
          }
          map[curParam] = new ArgInfo<>(args[curArg], types[curArg]);
          notOptional--;
          curArg++;
          curParam++;
        }
      }

      List<Arg> varargs = new ArrayList<>();
      for (; curArg < args.length; curArg++) {
        if (!isAssignableByConversion(vararg, types[curArg], context)) {
          for (int i = startParam; i < curParam; i++) map[i] = null;
          return false;
        }
        varargs.add(args[curArg]);
      }
      map[paramLength] = new ArgInfo<>(varargs, true, new PsiEllipsisType(vararg));
      return true;
    }
  }

  private static int getOptionalParamCount(@NotNull GrSignature signature, PsiElement context) {
    GrClosureParameter[] parameters = signature.getParameters();
    boolean isCompileStatic = PsiUtil.isCompileStatic(context);
    if (parameters.length == 1 && !(parameters[0].getType() instanceof PsiPrimitiveType) && !signature.isCurried() && !isCompileStatic)
      return 1;
    int count = 0;
    for (GrClosureParameter parameter : parameters) {
      if (parameter.isOptional()) count++;
    }
    return count;
  }

  public static class ArgInfo<ArgType> {
    private static final ArgInfo[] EMPTY_ARRAY = new ArgInfo[0];
    private static final ArgInfo<?> EMPTY = new ArgInfo<>(Collections.emptyList(), false, null);

    public final @NotNull List<ArgType> args;
    public final boolean isMultiArg;
    public final @Nullable PsiType type;

    public ArgInfo(@NotNull List<ArgType> args, boolean multiArg, @Nullable PsiType type) {
      this.args = args;
      isMultiArg = multiArg;
      this.type = type;
    }

    public ArgInfo(ArgType arg, PsiType type) {
      this(Collections.singletonList(arg), false, type);
    }

    @Contract(pure = true)
    @NotNull
    public static <ArgType> ArgInfo<ArgType> empty() {
      //noinspection unchecked
      return (ArgInfo<ArgType>)EMPTY;
    }

    @Contract(pure = true)
    public static <ArgType> ArgInfo<ArgType> @NotNull [] empty_array() {
      //noinspection unchecked
      return EMPTY_ARRAY;
    }
  }

  private static class InnerArg {
    List<PsiElement> list;
    PsiType type;

    InnerArg(PsiType type, PsiElement... elements) {
      this.list = new ArrayList<>(Arrays.asList(elements));
      this.type = type;
    }
  }

  @Nullable
  public static Map<GrExpression, Pair<PsiParameter, PsiType>> mapArgumentsToParameters(@NotNull GroovyResolveResult resolveResult,
                                                                                        @NotNull PsiElement context,
                                                                                        final boolean partial,
                                                                                        final boolean eraseArgs,
                                                                                        final GrNamedArgument @NotNull [] namedArgs,
                                                                                        final GrExpression @NotNull [] expressionArgs,
                                                                                        GrClosableBlock @NotNull [] closureArguments) {
    final GrSignature signature;
    final PsiParameter[] parameters;
    final PsiElement element = resolveResult.getElement();
    PsiSubstitutor substitutor;
    if (resolveResult instanceof GroovyMethodResult) {
      substitutor = ((GroovyMethodResult)resolveResult).getPartialSubstitutor();
    }
    else {
      substitutor = resolveResult.getSubstitutor();
    }
    if (element instanceof PsiMethod) {
      signature = createSignature((PsiMethod)element, substitutor, eraseArgs);
      parameters = ((PsiMethod)element).getParameterList().getParameters();
    }
    else if (element instanceof GrFunctionalExpression) {
      signature = createSignature(((GrFunctionalExpression)element));
      parameters = ((GrFunctionalExpression)element).getAllParameters();
    }
    else {
      return null;
    }

    final ArgInfo<PsiElement>[] argInfos = mapParametersToArguments(signature, namedArgs, expressionArgs, closureArguments, context, partial, eraseArgs);
    if (argInfos == null) {
      return null;
    }

    final Map<GrExpression, Pair<PsiParameter, PsiType>> result = new HashMap<>();
    for (int i = 0; i < argInfos.length; i++) {
      ArgInfo<PsiElement> info = argInfos[i];
      if (info == null) continue;
      for (PsiElement arg : info.args) {
        if (arg instanceof GrNamedArgument) {
          arg = ((GrNamedArgument)arg).getExpression();
        }
        final GrExpression expression = (GrExpression)arg;
        PsiType type = parameters[i].getType();
        if (info.isMultiArg && type instanceof PsiArrayType) {
          type = ((PsiArrayType)type).getComponentType();
        }
        result.put(expression, Pair.create(parameters[i], substitutor.substitute(type)));
      }
    }

    return result;
  }


  public static ArgInfo<PsiElement> @Nullable [] mapParametersToArguments(@NotNull GrSignature signature, @NotNull GrCall call) {
    return mapParametersToArguments(signature, call.getNamedArguments(), call.getExpressionArguments(), call.getClosureArguments(), call,
                                    false, false);
  }

  public static ArgInfo<PsiElement> @Nullable [] mapParametersToArguments(@NotNull GrSignature signature,
                                                                          GrNamedArgument @NotNull [] namedArgs,
                                                                          GrExpression @NotNull [] expressionArgs,
                                                                          GrClosableBlock @NotNull [] closureArguments,
                                                                          @NotNull PsiElement context,
                                                                          boolean partial, boolean eraseArgs) {
    List<InnerArg> innerArgs = new ArrayList<>();

    boolean hasNamedArgs = namedArgs.length > 0;
    GrClosureParameter[] params = signature.getParameters();

    if (hasNamedArgs) {
      if (params.length == 0) return null;
      PsiType type = params[0].getType();
      if (InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_UTIL_MAP) ||
          type == null ||
          type.equalsToText(CommonClassNames.JAVA_LANG_OBJECT)) {
        innerArgs.add(new InnerArg(createTypeByFQClassName(JAVA_UTIL_LINKED_HASH_MAP, context), namedArgs));
      }
      else {
        return null;
      }
    }

    for (GrExpression expression : expressionArgs) {
      PsiType type = expression.getType();
      if (partial && expression instanceof GrNewExpression && com.intellij.psi.util.PsiUtil.resolveClassInType(type) == null) {
        type = null;
      }
      if (eraseArgs) {
        type = TypeConversionUtil.erasure(type);
      }
      innerArgs.add(new InnerArg(type, expression));
    }

    for (GrClosableBlock closureArgument : closureArguments) {
      innerArgs.add(new InnerArg(TypeConversionUtil.erasure(closureArgument.getType()), closureArgument));
    }

    return mapParametersToArguments(signature, innerArgs, hasNamedArgs, partial, context);
  }

  private static ArgInfo<PsiElement>[] mapParametersToArguments(@NotNull GrSignature signature,
                                                                @NotNull List<? extends InnerArg> innerArgs,
                                                                boolean hasNamedArgs,
                                                                boolean partial,
                                                                @NotNull PsiElement context) {
    final ArgInfo<InnerArg>[] innerMap = mapParametersToArguments(signature, innerArgs.toArray(new InnerArg[0]), o -> o.type, context, partial);
    if (innerMap == null) return null;

    //noinspection unchecked
    ArgInfo<PsiElement>[] map = new ArgInfo[innerMap.length];
    int i = 0;
    if (hasNamedArgs) {
      map[i] = new ArgInfo<>(innerMap[i].args.iterator().next().list, true, innerArgs.get(i).type);
      i++;
    }

    for (; i < innerMap.length; i++) {
      final ArgInfo<InnerArg> innerArg = innerMap[i];
      if (innerArg == null) {
        map[i] = null;
      }
      else {
        List<PsiElement> argList = new ArrayList<>();
        for (InnerArg arg : innerArg.args) {
          argList.addAll(arg.list);
        }
        boolean multiArg = innerArg.isMultiArg || argList.size() > 1;
        map[i] = new ArgInfo<>(argList, multiArg, innerArg.type);
      }
    }

    return map;
  }

  public static List<MethodSignature> generateAllSignaturesForMethod(GrMethod method, PsiSubstitutor substitutor) {
    GrSignature signature = createSignature(method, substitutor);
    String name = method.getName();
    PsiTypeParameter[] typeParameters = method.getTypeParameters();

    final ArrayList<MethodSignature> result = new ArrayList<>();
    generateAllMethodSignaturesByClosureSignature(name, signature, typeParameters, substitutor, result);
    return result;
  }

  @NotNull
  public static MultiMap<MethodSignature, PsiMethod> findRawMethodSignatures(PsiMethod @NotNull [] methods, @NotNull PsiClass clazz) {
    Map<PsiTypeParameter, PsiType> initialMap = new HashMap<>();

    for (PsiTypeParameter parameter : clazz.getTypeParameters()) {
      initialMap.put(parameter, null);
    }

    final PsiSubstitutor initialSubstitutor = PsiSubstitutor.createSubstitutor(initialMap);

    MultiMap<MethodSignature, PsiMethod> result = new MultiMap<>();
    for (PsiMethod method : methods) {
      final PsiMethod actual = method instanceof GrReflectedMethod ? ((GrReflectedMethod)method).getBaseMethod() : method;

      PsiSubstitutor substitutor = calcRawSubstitutor(initialMap, initialSubstitutor, actual);
      result.putValue(method.getSignature(substitutor), actual);
    }

    return result;
  }

  @NotNull
  private static PsiSubstitutor calcRawSubstitutor(@NotNull Map<PsiTypeParameter, PsiType> initialMap,
                                                   @NotNull PsiSubstitutor initialSubstitutor,
                                                   @NotNull PsiMethod actual) {
    if (actual.hasTypeParameters()) {
      final HashMap<PsiTypeParameter, PsiType> map1 = new HashMap<>(initialMap);
      for (PsiTypeParameter parameter : actual.getTypeParameters()) {
        map1.put(parameter, null);
      }
      return PsiSubstitutor.createSubstitutor(map1);
    }
    else {
      return initialSubstitutor;
    }
  }

  private static MethodSignature generateSignature(String name,
                                                   List<PsiType> paramTypes,
                                                   PsiTypeParameter[] typeParameters,
                                                   PsiSubstitutor substitutor) {
    return MethodSignatureUtil.createMethodSignature(name, paramTypes.toArray(PsiType.createArray(paramTypes.size())), typeParameters, substitutor);
  }

  public static void generateAllMethodSignaturesByClosureSignature(@NotNull String name,
                                                                   @NotNull GrSignature signature,
                                                                   PsiTypeParameter @NotNull [] typeParameters,
                                                                   @NotNull PsiSubstitutor substitutor,
                                                                   List<? super MethodSignature> result) {
    GrClosureParameter[] params = signature.getParameters();

    ArrayList<PsiType> newParams = new ArrayList<>(params.length);
    ArrayList<GrClosureParameter> opts = new ArrayList<>(params.length);
    ArrayList<Integer> optInds = new ArrayList<>(params.length);

    for (int i = 0; i < params.length; i++) {
      if (params[i].isOptional()) {
        opts.add(params[i]);
        optInds.add(i);
      }
      else {
        newParams.add(params[i].getType());
      }
    }

    result.add(generateSignature(name, newParams, typeParameters, substitutor));
    for (int i = 0; i < opts.size(); i++) {
      newParams.add(optInds.get(i), opts.get(i).getType());
      result.add(generateSignature(name, newParams, typeParameters, substitutor));
    }
  }

  public static List<MethodSignature> generateAllMethodSignaturesBySignature(@NotNull final String name,
                                                                             @NotNull final List<? extends GrSignature> signatures) {
    final ArrayList<MethodSignature> result = new ArrayList<>();

    for (GrSignature signature : signatures) {
      generateAllMethodSignaturesByClosureSignature(name, signature, PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.EMPTY, result);
    }

    return result;
  }

  @Nullable
  public static PsiType getTypeByArg(ArgInfo<? extends PsiElement> arg, PsiManager manager, GlobalSearchScope resolveScope) {
    if (arg.isMultiArg) {
      if (arg.args.isEmpty()) return LazyFqnClassType.getLazyType(CommonClassNames.JAVA_LANG_OBJECT, LanguageLevel.JDK_1_5, resolveScope,
                                                                  JavaPsiFacade.getInstance(manager.getProject())).createArrayType();
      PsiType leastUpperBound = null;
      PsiElement first = arg.args.get(0);
      if (first instanceof GrNamedArgument) {
        GrNamedArgument[] args=new GrNamedArgument[arg.args.size()];
        for (int i = 0, size = arg.args.size(); i < size; i++) {
          args[i] = (GrNamedArgument)arg.args.get(i);
        }
        return GrMapType.createFromNamedArgs(first, args);
      }
      else {
        for (PsiElement elem : arg.args) {
          if (elem instanceof GrExpression) {
            leastUpperBound = TypesUtil.getLeastUpperBoundNullable(leastUpperBound, ((GrExpression)elem).getType(), manager);
          }
        }
        if (leastUpperBound == null) return null;
        return leastUpperBound.createArrayType();
      }
    }
    else {
      if (arg.args.isEmpty()) return null;
      PsiElement elem = arg.args.get(0);
      if (elem instanceof GrExpression) {
        return ((GrExpression)elem).getType();
      }
      return null;
    }
  }


  /**
   * @return return type or null if there is some different return types
   */
  @Nullable
  public static PsiType getReturnType(List<? extends GrSignature> signatures) {
    if (signatures.size() == 1) {
      return signatures.get(0).getReturnType();
    }
    else if (signatures.size() > 1) {
      final PsiType type = signatures.get(0).getReturnType();
      if (type == null) return null;
      String firstType = type.getCanonicalText();
      for (int i = 1; i < signatures.size(); i++) {
        final PsiType _type = signatures.get(i).getReturnType();
        if (_type == null) return null;
        if (!firstType.equals(_type.getCanonicalText())) return null;
      }
      return type;
    }
    else {
      return null;
    }
  }


  public static class MapResultWithError {
    private final List<Pair<Integer, PsiType>> errorsAndExpectedType;

    public MapResultWithError(List<Pair<Integer, PsiType>> errorsAndExpectedType) {
      this.errorsAndExpectedType = errorsAndExpectedType;
    }

    public List<Pair<Integer, PsiType>> getErrors() {
      return errorsAndExpectedType;
    }
  }

  @Nullable
  public static <Arg> MapResultWithError mapSimpleSignatureWithErrors(@NotNull GrSignature signature,
                                                                      Arg @NotNull [] args,
                                                                      @NotNull Function<? super Arg, ? extends PsiType> typeComputer,
                                                                      @NotNull GroovyPsiElement context,
                                                                      int maxErrorCount) {
    final GrClosureParameter[] params = signature.getParameters();
    if (args.length < params.length) return null;

    if (args.length > params.length && !signature.isVarargs()) return null;

    int errorCount = 0;
    List<Pair<Integer, PsiType>> errors = new ArrayList<>(maxErrorCount);

    for (int i = 0; i < params.length; i++) {
      final PsiType type = typeComputer.fun(args[i]);
      final GrClosureParameter parameter = params[i];
      final PsiType parameterType = parameter.getType();
      if (isAssignableByConversion(parameterType, type, context)) continue;
      if (parameterType instanceof PsiArrayType && i == params.length - 1) {
        if (i + 1 == args.length) {
          errors.add(new Pair<>(i, parameterType));
        }
        final PsiType ellipsis = ((PsiArrayType)parameterType).getComponentType();
        for (int j = i; j < args.length; j++) {
          if (!isAssignableByConversion(ellipsis, typeComputer.fun(args[j]), context)) {
            errorCount++;
            if (errorCount > maxErrorCount) return null;
            errors.add(new Pair<>(i, ellipsis));
          }
        }
      }
      else {
        errorCount++;
        if (errorCount > maxErrorCount) return null;
        errors.add(new Pair<>(i, parameterType));
      }
    }
    return new MapResultWithError(errors);
  }

  public static List<GrSignature> generateSimpleSignatures(@NotNull List<? extends GrSignature> signatures) {
    final List<GrSignature> result = new ArrayList<>();
    for (GrSignature signature : signatures) {
      final GrClosureParameter[] original = signature.getParameters();
      final ArrayList<GrClosureParameter> parameters = new ArrayList<>(original.length);

      for (GrClosureParameter parameter : original) {
        parameters.add(new GrDelegatingClosureParameter(parameter) {
          @Override
          public boolean isOptional() {
            return false;
          }

          @Nullable
          @Override
          public GrExpression getDefaultInitializer() {
            return null;
          }
        });
      }

      final int pCount = signature.isVarargs() ? signature.getParameterCount() - 2 : signature.getParameterCount() - 1;
      for (int i = pCount; i >= 0; i--) {
        if (original[i].isOptional()) {
          result.add(new GrImmediateClosureSignatureImpl(parameters.toArray(GrClosureParameter.EMPTY_ARRAY), signature.getReturnType(), signature.isVarargs(), false));
          parameters.remove(i);
        }
      }
      result.add(new GrImmediateClosureSignatureImpl(parameters.toArray(GrClosureParameter.EMPTY_ARRAY), signature.getReturnType(), signature.isVarargs(), false));
    }
    return result;
  }

  @Nullable
  public static GrMethodCall findCall(@NotNull GrFunctionalExpression expression) {
    PsiElement parent = expression.getParent();
    if (parent instanceof GrMethodCall && ArrayUtil.contains(expression, ((GrMethodCall)parent).getClosureArguments())) {
      return (GrMethodCall)parent;
    }

    if (parent instanceof GrArgumentList) {
      PsiElement grandparent = parent.getParent();
      if (grandparent instanceof GrMethodCall) {
        return (GrMethodCall)grandparent;
      }
    }

    return null;
  }
}
