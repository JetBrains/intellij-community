// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.GrMethodComparator;
import org.jetbrains.plugins.groovy.lang.resolve.GrResolverProcessor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.newSmartList;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.singleOrValid;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.valid;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolveKind.*;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.inference.InferenceKt.buildTopLevelArgumentTypes;

class GroovyResolverProcessorImpl extends GroovyResolverProcessor
  implements GrMethodComparator.Context, GrResolverProcessor<GroovyResolveResult> {

  private final @NotNull PsiType[] myTypeArguments;
  private final @NotNull NullableLazyValue<PsiType[]> myArgumentTypes;

  GroovyResolverProcessorImpl(@NotNull final GrReferenceExpression ref, @NotNull EnumSet<GroovyResolveKind> kinds) {
    super(ref, kinds);
    myTypeArguments = ref.getTypeArguments();
    myArgumentTypes = NullableLazyValue.createValue(() -> buildTopLevelArgumentTypes((GrCall)myRef.getParent()));
  }

  @Override
  @NotNull
  public List<GroovyResolveResult> getResults() {
    final List<GroovyResolveResult> variables = getCandidates(VARIABLE);
    if (!variables.isEmpty()) {
      return variables;
    }

    final List<? extends GroovyResolveResult> methods = getAllCandidates(METHOD);
    final List<? extends GroovyResolveResult> properties = getAllCandidates(PROPERTY);
    final List<? extends GroovyResolveResult> fields = getAllCandidates(FIELD);

    final boolean hasAnyMethods = !methods.isEmpty();
    final boolean hasAnyProperties = !properties.isEmpty();
    final boolean hasAnyFields = !fields.isEmpty();

    if (hasAnyMethods && !hasAnyProperties && !hasAnyFields) {
      // don't compute isApplicable on a single method result if there are no properties or fields
      if (methods.size() == 1) {
        return new SmartList<>(methods);
      }
      else {
        List<GroovyResolveResult> validMethods = valid(methods);
        if (!validMethods.isEmpty()) {
          return filterMethodCandidates(validMethods);
        }
      }
    }
    else if (!hasAnyMethods && hasAnyProperties && !hasAnyFields) {
      return singleOrValid(properties);
    }
    else if (!hasAnyMethods && !hasAnyProperties && hasAnyFields) {
      return singleOrValid(fields);
    }

    final List<GroovyResolveResult> validMethods = valid(methods);
    if (!validMethods.isEmpty()) {
      return filterMethodCandidates(validMethods);
    }
    final List<GroovyResolveResult> validProperties = valid(properties);
    if (!validProperties.isEmpty()) {
      return validProperties.size() == 1 ? validProperties : newSmartList(validProperties.get(0));
    }
    final List<GroovyResolveResult> validFields = valid(fields);
    if (!validFields.isEmpty()) {
      return validFields;
    }
    if (!validProperties.isEmpty()) {
      return validProperties;
    }

    final List<GroovyResolveResult> bindings = getCandidates(BINDING);
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
    final List<GroovyResolveResult> result = newSmartList();
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
