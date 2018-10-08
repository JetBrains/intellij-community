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
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import kotlin.Lazy;
import kotlin.LazyKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.BaseGroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.resolve.GrResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.MethodResolveResult;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.concat;
import static java.util.Collections.singletonList;
import static org.jetbrains.plugins.groovy.lang.psi.util.PropertyUtilKt.isPropertyName;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.singleOrValid;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.valid;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.inference.InferenceKt.buildTopLevelArgumentTypes;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.inference.InferenceKt.getTopLevelTypeCached;

public abstract class GroovyResolverProcessor implements PsiScopeProcessor, ElementClassHint, NameHint, DynamicMembersHint, MultiProcessor {

  protected final @NotNull GrReferenceExpression myRef;
  private final @NotNull String myName;
  protected final @NotNull EnumSet<GroovyResolveKind> myAcceptableKinds;

  private final boolean myIsLValue;

  protected final @NotNull PsiType[] myTypeArguments;
  protected final @NotNull NullableLazyValue<PsiType[]> myArgumentTypes;

  protected final List<GrResolverProcessor<? extends GroovyResolveResult>> myAccessorProcessors;
  protected final MultiMap<GroovyResolveKind, GroovyResolveResult> myCandidates = MultiMap.create();

  private boolean myCheckValidMethods = false;
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
      myArgumentTypes = NullableLazyValue.createValue(() -> buildTopLevelArgumentTypes(myRef));
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
    final Lazy<PsiType> receiverType = LazyKt.lazy(() -> getTopLevelQualifierType());
    if (myIsLValue) {
      return singletonList(
        new PropertyProcessor(receiverType, myName, PropertyKind.SETTER, () -> myArgumentTypes.getValue(), myRef)
      );
    }
    return ContainerUtil.newArrayList(
      new PropertyProcessor(receiverType, myName, PropertyKind.GETTER, () -> PsiType.EMPTY_ARRAY, myRef),
      new PropertyProcessor(receiverType, myName, PropertyKind.BOOLEAN_GETTER, () -> PsiType.EMPTY_ARRAY, myRef)
    );
  }

  public PsiType getTopLevelQualifierType() {
    GrExpression expression = myRef.getQualifierExpression();
    if (expression instanceof GrMethodCallExpression) {
      return getTopLevelTypeCached(expression);
    }
    else {
      return PsiImplUtil.getQualifierType(myRef);
    }
  }

  public boolean isPropertyResolve() {
    return myAcceptableKinds.contains(GroovyResolveKind.PROPERTY);
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
      if (myCheckValidMethods) {
        myCheckValidMethods = false;
        if (!valid(myCandidates.get(GroovyResolveKind.METHOD)).isEmpty()) {
          myStopExecutingMethods = true;
          return true;
        }
      }
    }
    else {
      if (!myCandidates.get(kind).isEmpty()) {
        return true;
      }
    }

    final GroovyResolveResult candidate;
    if (kind == GroovyResolveKind.METHOD) {
      candidate = new MethodResolveResult((PsiMethod)namedElement, myRef, state);
    }
    else {
      candidate = new BaseGroovyResolveResult<>(namedElement, myRef, state);
    }

    myCandidates.putValue(kind, candidate);

    if (kind == GroovyResolveKind.VARIABLE && candidate.isValidResult()) {
      myStopExecutingMethods = true;
    }

    return true;
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
    if (JavaScopeProcessorEvent.CHANGE_LEVEL == event) {
      myCheckValidMethods = true;
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
  @Override
  public Collection<? extends PsiScopeProcessor> getProcessors() {
    return myStopExecutingMethods ? singletonList(this)
                                  : concat(singletonList(this), myAccessorProcessors);
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
  protected List<GroovyResolveResult> getAllCandidates(@NotNull GroovyResolveKind kind) {
    if (kind == GroovyResolveKind.PROPERTY) {
      final List<GroovyResolveResult> results = ContainerUtil.newSmartList();
      myAccessorProcessors.forEach(it -> results.addAll(it.getResults()));
      return results;
    }
    else {
      return new SmartList<>(myCandidates.get(kind));
    }
  }

  @NotNull
  protected List<GroovyResolveResult> getCandidates(@NotNull GroovyResolveKind kind) {
    return singleOrValid(getAllCandidates(kind));
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
}
