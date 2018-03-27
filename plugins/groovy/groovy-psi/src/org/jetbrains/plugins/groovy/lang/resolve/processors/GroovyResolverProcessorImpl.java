/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.GrMethodComparator;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil.collapseReflectedMethods;

class GroovyResolverProcessorImpl extends GroovyResolverProcessor implements GrMethodComparator.Context {

  private final boolean myIsPartOfFqn;

  GroovyResolverProcessorImpl(@NotNull final GrReferenceExpression ref, @NotNull EnumSet<GroovyResolveKind> kinds, boolean forceRValue) {
    super(ref, kinds, null, forceRValue);
    myIsPartOfFqn = ResolveUtil.isPartOfFQN(ref);
  }

  @NotNull
  public List<GroovyResolveResult> getCandidates() {
    List<GroovyResolveResult> candidates;

    candidates = getCandidates(GroovyResolveKind.VARIABLE);
    if (!candidates.isEmpty()) {
      return candidates;
    }

    candidates = getCandidates(GroovyResolveKind.METHOD);
    if (!candidates.isEmpty()) {
      final List<GroovyResolveResult> results = filterMethodCandidates(candidates);
      return myRef.hasMemberPointer() ? collapseReflectedMethods(results) : results;
    }

    candidates = getCandidates(GroovyResolveKind.ENUM_CONST);
    if (!candidates.isEmpty()) {
      return candidates;
    }

    candidates = getCandidates(GroovyResolveKind.FIELD);
    if (!candidates.isEmpty()) {
      assert candidates.size() == 1;
      final GroovyResolveResult candidate = candidates.get(0);
      final PsiElement element = candidate.getElement();
      if (element instanceof PsiField) {
        final PsiClass containingClass = ((PsiField)element).getContainingClass();
        if (containingClass != null && PsiUtil.getContextClass(myRef) == containingClass) return candidates;
      }
      else if (!(element instanceof GrBindingVariable)) {
        return candidates;
      }
    }

    if (myIsPartOfFqn) {
      candidates = getCandidates(GroovyResolveKind.PACKAGE, GroovyResolveKind.CLASS);
      if (!candidates.isEmpty()) {
        return candidates;
      }
    }

    candidates = getCandidates(GroovyResolveKind.PROPERTY);
    if (!candidates.isEmpty()) {
      return candidates.size() <= 1 ? candidates : ContainerUtil.newSmartList(candidates.get(0));
    }

    candidates = getCandidates(GroovyResolveKind.FIELD);
    if (!candidates.isEmpty()) {
      return candidates;
    }

    candidates = getCandidates(GroovyResolveKind.PACKAGE, GroovyResolveKind.CLASS);
    if (!candidates.isEmpty()) {
      return candidates;
    }

    candidates = getCandidates(GroovyResolveKind.PROPERTY);
    if (!candidates.isEmpty()) {
      return candidates;
    }

    candidates = getCandidates(GroovyResolveKind.BINDING);
    if (!candidates.isEmpty()) {
      return candidates;
    }

    for (GroovyResolveKind kind : myAcceptableKinds) {
      Collection<GroovyResolveResult> results = myInapplicableCandidates.get(kind);
      if (!results.isEmpty()) {
        return ContainerUtil.newArrayList(ResolveUtil.filterSameSignatureCandidates(
          filterCorrectParameterCount(results)
        ));
      }
    }

    return Collections.emptyList();
  }

  private List<GroovyResolveResult> filterCorrectParameterCount(Collection<GroovyResolveResult> candidates) {
    PsiType[] argumentTypes = myArgumentTypes.getValue();
    if (argumentTypes == null) return ContainerUtil.newArrayList(candidates);
    final List<GroovyResolveResult> result = ContainerUtil.newSmartList();
    for (GroovyResolveResult candidate : candidates) {
      if (candidate instanceof GroovyMethodResult) {
        if (((GroovyMethodResult)candidate).getElement().getParameterList().getParametersCount() == argumentTypes.length) {
          result.add(candidate);
        }
      }
      else {
        result.add(candidate);
      }
    }
    if (!result.isEmpty()) return result;
    return ContainerUtil.newArrayList(candidates);
  }

  private List<GroovyResolveResult> filterMethodCandidates(List<GroovyResolveResult> candidates) {
    if (candidates.size() <= 1) return candidates;

    final List<GroovyResolveResult> results = ContainerUtil.newArrayList();
    final Iterator<GroovyResolveResult> itr = candidates.iterator();

    results.add(itr.next());

    Outer:
    while (itr.hasNext()) {
      final GroovyResolveResult resolveResult = itr.next();
      if (resolveResult instanceof GroovyMethodResult) {
        for (Iterator<GroovyResolveResult> iterator = results.iterator(); iterator.hasNext(); ) {
          final GroovyResolveResult otherResolveResult = iterator.next();
          if (otherResolveResult instanceof GroovyMethodResult) {
            int res = GrMethodComparator.compareMethods((GroovyMethodResult)resolveResult, (GroovyMethodResult)otherResolveResult, this);
            if (res > 0) {
              continue Outer;
            }
            else if (res < 0) {
              iterator.remove();
            }
          }
        }
      }

      results.add(resolveResult);
    }

    return results;
  }

  @Nullable
  @Override
  public PsiType[] getArgumentTypes() {
    return myArgumentTypes.getValue();
  }

  @Nullable
  @Override
  public PsiType[] getTypeArguments() {
    return myTypeArguments;
  }

  @Nullable
  @Override
  public PsiType getThisType() {
    return myThisType;
  }

  @NotNull
  @Override
  public PsiElement getPlace() {
    return myRef;
  }

  @Override
  public boolean isConstructor() {
    return false;
  }
}
