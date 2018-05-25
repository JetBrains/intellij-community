// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.lang.java.beans.PropertyKind;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyMethodResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.GrResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt;
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.Argument;
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSession;
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.GroovyInferenceSessionBuilder;
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.MethodCandidate;

import java.util.*;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.groovy.lang.psi.util.PropertyUtilKt.isPropertyName;
import static org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.isApplicable;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.isAccessible;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.isStaticsOK;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.inference.InferenceKt.getTopLevelType;

public abstract class GroovyResolverProcessor implements PsiScopeProcessor, ElementClassHint, NameHint, DynamicMembersHint {

  protected final @NotNull GrReferenceExpression myRef;
  private final @NotNull String myName;
  protected final @NotNull EnumSet<GroovyResolveKind> myAcceptableKinds;

  private final boolean myIsLValue;

  protected final @NotNull PsiType[] myTypeArguments;
  protected final @NotNull NullableLazyValue<PsiType[]> myArgumentTypes; // TODO: should be removed with new API

  protected final List<GrResolverProcessor<? extends GroovyResolveResult>> myAccessorProcessors;
  protected final MultiMap<GroovyResolveKind, GroovyResolveResult> myCandidates = MultiMap.create();
  protected final MultiMap<GroovyResolveKind, GroovyResolveResult> myInapplicableCandidates = MultiMap.create();

  private boolean myStopExecutingMethods = false;

  GroovyResolverProcessor(@NotNull GrReferenceExpression ref,
                          @NotNull EnumSet<GroovyResolveKind> kinds,
                          boolean forceRValue) {
    myRef = ref;
    myAcceptableKinds = kinds;
    myName = getReferenceName(ref);

    myIsLValue = !forceRValue && PsiUtil.isLValue(myRef);

    myTypeArguments = ref.getTypeArguments();
    if (kinds.contains(GroovyResolveKind.METHOD) || myIsLValue) {
      myArgumentTypes = NullableLazyValue.createValue(() -> PsiUtil.getArgumentTypes(ref, false, null));
    }
    else {
      myArgumentTypes = NullableLazyValue.createValue(() -> null);
    }

    myAccessorProcessors = calcAccessorProcessors();
  }

  private List<GrResolverProcessor<? extends GroovyResolveResult>> calcAccessorProcessors() {
    if (!isPropertyResolve() || !isPropertyName(myName)) {
      return Collections.emptyList();
    }
    if (myIsLValue) {
      return Collections.singletonList(
        new PropertyProcessor(getTopLevelQualifierType(), myName, PropertyKind.SETTER, () -> myArgumentTypes.getValue(), myRef)
      );
    }
    return ContainerUtil.newArrayList(
      new PropertyProcessor(getTopLevelQualifierType(), myName, PropertyKind.GETTER, () -> PsiType.EMPTY_ARRAY, myRef),
      new PropertyProcessor(getTopLevelQualifierType(), myName, PropertyKind.BOOLEAN_GETTER, () -> PsiType.EMPTY_ARRAY, myRef)
    );
  }

  public PsiType getTopLevelQualifierType() {
    GrExpression expression = myRef.getQualifierExpression();
    if (expression instanceof GrMethodCallExpression) {
      return getTopLevelType(expression);
    } else {
      return PsiImplUtil.getQualifierType(myRef);
    }
  }

  public boolean isPropertyResolve() {
    return myAcceptableKinds.contains(GroovyResolveKind.PROPERTY);
  }

  public static List<PsiScopeProcessor> allProcessors(PsiScopeProcessor processor) {
    if (processor instanceof GroovyResolverProcessor && !((GroovyResolverProcessor)processor).myStopExecutingMethods) {
      List<GrResolverProcessor<? extends GroovyResolveResult>> accessors = ((GroovyResolverProcessor)processor).myAccessorProcessors;
      if (!accessors.isEmpty()) {
        return ContainerUtil.concat(Collections.singletonList(processor), accessors);
      }
    }
    return Collections.singletonList(processor);
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (!(element instanceof PsiNamedElement)) return true;
    final PsiNamedElement namedElement = (PsiNamedElement)element;

    final String name = ResolveUtilKt.getName(state, namedElement);
    if (!myName.equals(name)) return true;

    final GroovyResolveKind kind = getResolveKind(namedElement);
    if (!myAcceptableKinds.contains(kind)) return true;

    if (kind == GroovyResolveKind.METHOD) {
      if (myStopExecutingMethods) {
        return true;
      }
    }
    else {
      if (!myCandidates.get(kind).isEmpty()) {
        return true;
      }
    }

    final PsiElement resolveContext = state.get(ClassHint.RESOLVE_CONTEXT);

    final SpreadState spreadState = state.get(SpreadState.SPREAD_STATE);
    final boolean isAccessible = isAccessible(myRef, namedElement);
    final boolean isStaticsOK = isStaticsOK(myRef, namedElement, resolveContext, false);

    final GroovyResolveResult candidate;

    if (kind == GroovyResolveKind.METHOD) {
      final PsiMethod method = (PsiMethod)namedElement;


      MethodCandidate methodCandidate = buildMethodCandidate(state, method);
      GroovyInferenceSession session = new GroovyInferenceSessionBuilder(myRef, methodCandidate).build();
      PsiSubstitutor applicabilitySubst = session.inferSubst();

      final boolean isApplicable = isApplicable(eraseTypes(methodCandidate.getArgumentTypes()), methodCandidate.getMethod(), applicabilitySubst, myRef, true);
      candidate = new GroovyMethodResultImpl(
        method, resolveContext, spreadState,
        applicabilitySubst,
        () -> {
          GroovyInferenceSession inferenceSession =
            new GroovyInferenceSessionBuilder(myRef, methodCandidate).addReturnConstraint().resolveMode(false).build();
          return inferenceSession.inferSubst();
        },
        methodCandidate,
        false,
        isAccessible, isStaticsOK, isApplicable
      );
    }
    else {
      final PsiSubstitutor substitutor = getSubstitutor(state);
      candidate = new GroovyResolveResultImpl(
        namedElement, resolveContext, spreadState, substitutor, isAccessible, isStaticsOK, false, true
      );
    }

    (candidate.isValidResult() ? myCandidates : myInapplicableCandidates).putValue(kind, candidate);

    if (candidate.isValidResult() && kind == GroovyResolveKind.VARIABLE) {
      myStopExecutingMethods = true;
    }

    return true;
  }


  private MethodCandidate buildMethodCandidate(@NotNull ResolveState state, PsiMethod method) {
    final PsiSubstitutor substitutor = getSubstitutor(state);
    final PsiSubstitutor siteSubstitutor = updateSubst(myRef.getTypeArguments(), method.getTypeParameters(), substitutor);
    GrExpression qualifierExpression = myRef.getQualifierExpression();
    Argument qualifierConstraint = buildQualifier(state, qualifierExpression);
    List<Argument> argumentConstraints = buildArguments();

    if (method instanceof GrGdkMethod) {
      ArrayList<Argument> arguments = new ArrayList<>();
      arguments.add(0, qualifierConstraint);
      arguments.addAll(argumentConstraints);
      return new MethodCandidate(((GrGdkMethod)method).getStaticMethod(), siteSubstitutor, null, arguments, myRef);
    }

    return new MethodCandidate(method, siteSubstitutor, qualifierConstraint, argumentConstraints, myRef);
  }

  Argument buildQualifier(@NotNull ResolveState state, GrExpression qualifierExpression) {
    final PsiType resolvedThis = state.get(ClassHint.THIS_TYPE);
    final SpreadState spreadState = state.get(SpreadState.SPREAD_STATE);

    if (qualifierExpression != null && spreadState == null) {
      return new Argument(null, qualifierExpression);
    }

    PsiType type = PsiImplUtil.getQualifierType(myRef);
    if (spreadState != null && type != null) {
      type = com.intellij.psi.util.PsiUtil.extractIterableTypeParameter(type, false);
    }

    return new Argument(resolvedThis != null ? resolvedThis : type, null);
  }

  List<Argument> buildArguments() {

    PsiElement parent = myRef.getParent();
    if (parent instanceof GrCall) {
      List<Argument> result = new ArrayList<>();
      GrCall call = (GrCall)parent;
      GrNamedArgument[] namedArgs = call.getNamedArguments();
      GrExpression[] expressions = call.getExpressionArguments();
      GrClosableBlock[] closures = call.getClosureArguments();

      if (namedArgs.length > 0) {
        GrNamedArgument context = namedArgs[0];
        result.add(new Argument(GrMapType.createFromNamedArgs(context, namedArgs), null));
      }

      GrExpression[] argExp = ArrayUtil.mergeArrays(expressions, closures);

      List<Argument> constraints = Arrays.stream(argExp).map((exp) -> new Argument(null, exp)).collect(Collectors.toList());
      result.addAll(constraints);
      return result;
    }

    PsiType[] argumentTypes = myArgumentTypes.getValue();
    if (argumentTypes == null) return Collections.emptyList();
    return Arrays.stream(argumentTypes).map((t) -> new Argument(t, null)).collect(Collectors.toList());
  }


  private static PsiSubstitutor updateSubst(PsiType[] arguments, PsiTypeParameter[] parameters, PsiSubstitutor substitutor) {
    if (arguments.length != parameters.length) return substitutor;
    for (int i = 0; i < arguments.length; i++) {
      substitutor.put(parameters[i], arguments[i]);
    }

    return substitutor;
  }

  @SuppressWarnings("unchecked")
  @Nullable
  @Override
  public <T> T getHint(@NotNull Key<T> hintKey) {
    if (hintKey == ElementClassHint.KEY || hintKey == NameHint.KEY || hintKey == DynamicMembersHint.KEY) {
      return (T)this;
    }
    return null;
  }

  @Override
  public void handleEvent(@NotNull Event event, @Nullable Object associated) {
    if (JavaScopeProcessorEvent.CHANGE_LEVEL == event && !myCandidates.get(GroovyResolveKind.METHOD).isEmpty()) {
      myStopExecutingMethods = true;
    }
  }

  @Override
  public boolean shouldProcess(DeclarationKind kind) {
    if (kind == DeclarationKind.METHOD) {
      if (myStopExecutingMethods) return false;
      if (isPropertyResolve() && !myAcceptableKinds.contains(GroovyResolveKind.METHOD)) return false;
    }
    for (GroovyResolveKind resolveKind : myAcceptableKinds) {
      if (resolveKind.declarationKinds.contains(kind)) return true;
    }
    return false;
  }

  @NotNull
  @Override
  public String getName(@NotNull ResolveState state) {
    return myName;
  }

  @Override
  public boolean shouldProcessMethods() {
    return myRef.getParent() instanceof GrCallExpression && !myCandidates.containsKey(GroovyResolveKind.METHOD);
  }

  @Override
  public boolean shouldProcessProperties() {
    return true;
  }

  @NotNull
  public abstract List<GroovyResolveResult> getCandidates();

  public final GroovyResolveResult[] getCandidatesArray() {
    final List<GroovyResolveResult> candidates = getCandidates();
    final int size = candidates.size();
    if (size == 0) return GroovyResolveResult.EMPTY_ARRAY;
    if (size == 1) return new GroovyResolveResult[]{candidates.get(0)};
    return candidates.toArray(new GroovyResolveResult[size]);
  }

  private static GroovyResolveKind getResolveKind(PsiNamedElement element) {
    if (element instanceof PsiClass) {
      return GroovyResolveKind.CLASS;
    }
    else if (element instanceof PsiPackage) {
      return GroovyResolveKind.PACKAGE;
    }
    if (element instanceof PsiMethod) {
      return GroovyResolveKind.METHOD;
    }
    else if (element instanceof PsiEnumConstant) {
      return GroovyResolveKind.ENUM_CONST;
    }
    else if (element instanceof PsiField) {
      return GroovyResolveKind.FIELD;
    }
    else if (element instanceof GrBindingVariable) {
      return GroovyResolveKind.BINDING;
    }
    else if (element instanceof PsiVariable) {
      return GroovyResolveKind.VARIABLE;
    }
    else {
      return null;
    }
  }

  @NotNull
  protected List<GroovyResolveResult> getCandidates(@NotNull GroovyResolveKind... kinds) {
    return getCandidates(true, kinds);
  }

  @NotNull
  protected List<GroovyResolveResult> getCandidates(boolean applicable, @NotNull GroovyResolveKind... kinds) {
    MultiMap<GroovyResolveKind, GroovyResolveResult> map = applicable ? myCandidates : myInapplicableCandidates;
    final List<GroovyResolveResult> results = ContainerUtil.newSmartList();
    for (GroovyResolveKind kind : kinds) {
      if (kind == GroovyResolveKind.PROPERTY) {
        myAccessorProcessors.forEach(
          it -> it.getResults().stream().filter(
            result -> applicable == result.isValidResult()
          ).forEach(results::add)
        );
      }
      else {
        results.addAll(map.get(kind));
      }
    }
    return results;
  }

  @NotNull
  protected static PsiSubstitutor getSubstitutor(@NotNull final ResolveState state) {
    PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
    if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;
    return substitutor;
  }

  @NotNull
  private static String getReferenceName(@NotNull GrReferenceExpression ref) {
    final String name = ref.getReferenceName();
    assert name != null : "Reference name cannot be null";
    return name;
  }

  @Nullable
  private static PsiType[] eraseTypes(@Nullable PsiType[] types) {
    final PsiType[] erasedTypes = types == null ? null : Arrays.copyOf(types, types.length);
    if (erasedTypes != null) {
      for (int i = 0; i < types.length; i++) {
        erasedTypes[i] = TypeConversionUtil.erasure(erasedTypes[i]);
      }
    }
    return erasedTypes;
  }
}
