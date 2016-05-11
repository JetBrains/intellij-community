/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.util.NotNullComputable;
import com.intellij.psi.*;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.GrMethodComparator;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.RESOLVE_CONTEXT;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.RESOLVE_KINDS_METHOD_PROPERTY;

/**
 * @author ven
 */
public class MethodResolverProcessor extends ResolverProcessor<GroovyMethodResult> implements GrMethodComparator.Context {
  private final PsiType myThisType;

  @Nullable
  private final PsiType[] myArgumentTypes;

  private final boolean myAllVariants;

  private Set<GroovyMethodResult> myInapplicableCandidates = null;

  private final boolean myIsConstructor;

  private boolean myStopExecuting = false;

  private final SubstitutorComputer mySubstitutorComputer;

  public MethodResolverProcessor(@Nullable String name,
                                 @NotNull PsiElement place,
                                 boolean isConstructor,
                                 @Nullable PsiType thisType,
                                 @Nullable PsiType[] argumentTypes,
                                 @Nullable PsiType[] typeArguments) {
    this(name, place, isConstructor, thisType, argumentTypes, typeArguments, false);
  }

  public MethodResolverProcessor(@Nullable String name,
                                 @NotNull PsiElement place,
                                 boolean isConstructor,
                                 @Nullable PsiType thisType,
                                 @Nullable PsiType[] argumentTypes,
                                 @Nullable PsiType[] typeArguments,
                                 boolean allVariants) {
    super(name, RESOLVE_KINDS_METHOD_PROPERTY, place);
    myIsConstructor = isConstructor;
    myThisType = thisType;
    mySubstitutorComputer = new SubstitutorComputer(myThisType, argumentTypes, typeArguments, myPlace, myPlace.getParent());
    myArgumentTypes = argumentTypes == null ? null : Arrays.copyOf(argumentTypes, argumentTypes.length);
    if (myArgumentTypes != null) {
      for (int i = 0; i < myArgumentTypes.length; i++) {
        myArgumentTypes[i] = TypeConversionUtil.erasure(myArgumentTypes[i]);
      }
    }
    myAllVariants = allVariants;
  }


  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    if (myStopExecuting) {
      return false;
    }
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)element;

      if (method.isConstructor() != myIsConstructor) return true;

      final PsiElement resolveContext = state.get(RESOLVE_CONTEXT);
      final SpreadState spreadState = state.get(SpreadState.SPREAD_STATE);
      final PsiSubstitutor partialSubstitutor = getSubstitutor(state);
      final NotNullComputable<PsiSubstitutor> substitutorComputer =
        () -> mySubstitutorComputer.obtainSubstitutor(partialSubstitutor, method, resolveContext);

      boolean isAccessible = isAccessible(method);
      boolean isStaticsOK = isStaticsOK(method, resolveContext, false);
      boolean isApplicable = PsiUtil.isApplicable(myArgumentTypes, method, null, myPlace, true);
      boolean isValidResult = isStaticsOK && isAccessible && isApplicable;

      GroovyMethodResult candidate = new GroovyMethodResult(
        method, resolveContext, spreadState, partialSubstitutor, substitutorComputer, isAccessible, isStaticsOK, isValidResult
      );

      if (!myAllVariants && isValidResult) {
        addCandidate(candidate);
      }
      else {
        addInapplicableCandidate(candidate);
      }
    }

    return true;
  }

  protected boolean addInapplicableCandidate(@NotNull GroovyMethodResult candidate) {
    if (myInapplicableCandidates == null) {
      myInapplicableCandidates = ContainerUtil.newLinkedHashSet();
    }
    return myInapplicableCandidates.add(candidate);
  }

  @NotNull
  protected static PsiSubstitutor getSubstitutor(@NotNull final ResolveState state) {
    PsiSubstitutor substitutor = state.get(PsiSubstitutor.KEY);
    if (substitutor == null) substitutor = PsiSubstitutor.EMPTY;
    return substitutor;
  }

  @Override
  @NotNull
  public GroovyResolveResult[] getCandidates() {
    if (!myAllVariants && hasApplicableCandidates()) {
      return filterCandidates();
    }
    if (myInapplicableCandidates != null && !myInapplicableCandidates.isEmpty()) {
      Set<GroovyMethodResult> resultSet = myAllVariants ? myInapplicableCandidates
                                                        : filterCorrectParameterCount(myInapplicableCandidates);
      return ResolveUtil.filterSameSignatureCandidates(resultSet);
    }
    return GroovyResolveResult.EMPTY_ARRAY;
  }

  private Set<GroovyMethodResult> filterCorrectParameterCount(Set<GroovyMethodResult> candidates) {
    if (myArgumentTypes == null) return candidates;
    Set<GroovyMethodResult> result = ContainerUtil.newLinkedHashSet();
    for (GroovyMethodResult candidate : candidates) {
      if (candidate.getElement().getParameterList().getParametersCount() == myArgumentTypes.length) {
        result.add(candidate);
      }
    }
    if (!result.isEmpty()) return result;
    return candidates;
  }

  private GroovyResolveResult[] filterCandidates() {
    List<GroovyMethodResult> array = getCandidatesInternal();
    if (array.size() == 1) return array.toArray(new GroovyResolveResult[array.size()]);

    List<GroovyMethodResult> result = ContainerUtil.newArrayList();

    Iterator<GroovyMethodResult> itr = array.iterator();

    result.add(itr.next());

    Outer:
    while (itr.hasNext()) {
      GroovyMethodResult resolveResult = itr.next();
      for (Iterator<GroovyMethodResult> iterator = result.iterator(); iterator.hasNext(); ) {
        final GroovyMethodResult otherResolveResult = iterator.next();
        int res = GrMethodComparator.compareMethods(resolveResult, otherResolveResult, this);
        if (res > 0) {
          continue Outer;
        }
        else if (res < 0) {
          iterator.remove();
        }
      }

      result.add(resolveResult);
    }

    return result.toArray(new GroovyResolveResult[result.size()]);
  }

  @Override
  public boolean hasCandidates() {
    return hasApplicableCandidates() || myInapplicableCandidates != null && !myInapplicableCandidates.isEmpty();
  }

  public boolean hasApplicableCandidates() {
    return super.hasCandidates();
  }

  @Override
  @Nullable
  public PsiType[] getArgumentTypes() {
    return myArgumentTypes;
  }

  @Nullable
  @Override
  public PsiType[] getTypeArguments() {
    return mySubstitutorComputer.getTypeArguments();
  }

  @Override
  public void handleEvent(@NotNull Event event, Object associated) {
    super.handleEvent(event, associated);
    if (JavaScopeProcessorEvent.CHANGE_LEVEL == event && hasApplicableCandidates()) {
      myStopExecuting = true;
    }
  }

  @Nullable
  @Override
  public PsiType getThisType() {
    return myThisType;
  }

  @NotNull
  @Override
  public PsiElement getPlace() {
    return myPlace;
  }

  @Override
  public boolean isConstructor() {
    return myIsConstructor;
  }
}
