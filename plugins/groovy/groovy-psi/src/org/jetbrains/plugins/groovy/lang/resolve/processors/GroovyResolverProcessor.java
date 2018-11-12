// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.lang.java.beans.PropertyKind;
import com.intellij.openapi.util.Key;
import com.intellij.psi.*;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;
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
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.*;

public abstract class GroovyResolverProcessor implements PsiScopeProcessor, ElementClassHint, NameHint, DynamicMembersHint, MultiProcessor {

  protected final @NotNull GrReferenceExpression myRef;
  private final @NotNull String myName;
  protected final @NotNull EnumSet<GroovyResolveKind> myAcceptableKinds;

  protected final List<GrResolverProcessor<? extends GroovyResolveResult>> myAccessorProcessors;
  protected final MultiMap<GroovyResolveKind, GroovyResolveResult> myCandidates = MultiMap.create();

  private boolean myCheckValidMethods = false;
  private boolean myStopExecutingMethods = false;

  GroovyResolverProcessor(@NotNull GrReferenceExpression ref, @NotNull EnumSet<GroovyResolveKind> kinds) {
    myRef = ref;
    myAcceptableKinds = kinds;
    myName = getReferenceName(ref);
    myAccessorProcessors = calcAccessorProcessors();
  }

  private List<GrResolverProcessor<? extends GroovyResolveResult>> calcAccessorProcessors() {
    if (!isPropertyResolve() || !isPropertyName(myName)) {
      return Collections.emptyList();
    }
    return ContainerUtil.newArrayList(
      new AccessorProcessor(myName, PropertyKind.GETTER, Collections.emptyList(), myRef),
      new AccessorProcessor(myName, PropertyKind.BOOLEAN_GETTER, Collections.emptyList(), myRef)
    );
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
  public boolean shouldProcess(@NotNull DeclarationKind kind) {
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
  protected List<GroovyResolveResult> getAllCandidates(@NotNull GroovyResolveKind kind) {
    List<GroovyResolveResult> results = new SmartList<>(myCandidates.get(kind));
    if (kind == GroovyResolveKind.PROPERTY) {
      myAccessorProcessors.forEach(it -> results.addAll(it.getResults()));
    }
    return results;
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
