// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.GrMethodComparator;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

class GroovyResolverProcessorImpl extends GroovyResolverProcessor implements GrMethodComparator.Context {

  GroovyResolverProcessorImpl(@NotNull final GrReferenceExpression ref, @NotNull EnumSet<GroovyResolveKind> kinds, boolean forceRValue) {
    super(ref, kinds, forceRValue);
  }

  @Override
  @NotNull
  public List<GroovyResolveResult> getCandidates() {
    final List<GroovyResolveResult> variables = getCandidates(GroovyResolveKind.VARIABLE);
    if (!variables.isEmpty()) {
      return variables;
    }

    final List<GroovyResolveResult> methods = getCandidates(GroovyResolveKind.METHOD);
    if (!methods.isEmpty()) {
      return filterMethodCandidates(methods);
    }

    final List<GroovyResolveResult> properties = getCandidates(GroovyResolveKind.PROPERTY);
    if (!properties.isEmpty()) {
      return properties.size() <= 1 ? properties : ContainerUtil.newSmartList(properties.get(0));
    }

    final List<GroovyResolveResult> fields = getCandidates(GroovyResolveKind.FIELD);
    if (!fields.isEmpty()) {
      return fields;
    }

    if (!properties.isEmpty()) {
      return properties;
    }

    final List<GroovyResolveResult> bindings = getCandidates(GroovyResolveKind.BINDING);
    if (!bindings.isEmpty()) {
      return bindings;
    }

    return getAllCandidates();
  }

  @NotNull
  protected List<GroovyResolveResult> getAllCandidates() {
    for (GroovyResolveKind kind : myAcceptableKinds) {
      List<GroovyResolveResult> results = getAllCandidates(kind);
      if (!results.isEmpty()) {
        return ContainerUtil.newArrayList(ResolveUtil.filterSameSignatureCandidates(
          filterCorrectParameterCount(results)
        ));
      }
    }

    return Collections.emptyList();
  }

  protected List<GroovyResolveResult> filterCorrectParameterCount(Collection<? extends GroovyResolveResult> candidates) {
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

  protected List<GroovyResolveResult> filterMethodCandidates(List<GroovyResolveResult> candidates) {
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
