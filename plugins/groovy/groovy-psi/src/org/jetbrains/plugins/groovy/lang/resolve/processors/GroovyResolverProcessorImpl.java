// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
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

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.newSmartList;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.singleOrValid;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.valid;
import static org.jetbrains.plugins.groovy.lang.resolve.impl.OverloadsKt.chooseOverloads;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolveKind.*;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.inference.InferenceKt.buildTopLevelArgumentTypes;

class GroovyResolverProcessorImpl extends GroovyResolverProcessor
  implements GrMethodComparator.Context, GrResolverProcessor<GroovyResolveResult> {

  private final @NotNull NullableLazyValue<PsiType[]> myArgumentTypes;

  GroovyResolverProcessorImpl(@NotNull final GrReferenceExpression ref, @NotNull EnumSet<GroovyResolveKind> kinds) {
    super(ref, kinds);
    myArgumentTypes = NullableLazyValue.createValue(() -> {
      PsiType[] types = buildTopLevelArgumentTypes((GrCall)myRef.getParent());
      return types == null ? null : ContainerUtil.map(types, TypeConversionUtil::erasure, PsiType.EMPTY_ARRAY);
    });
  }

  @Override
  @NotNull
  public List<? extends GroovyResolveResult> getResults() {
    final List<GroovyResolveResult> variables = getCandidates(VARIABLE);
    if (!variables.isEmpty()) {
      return variables;
    }

    //noinspection unchecked
    final List<? extends GroovyMethodResult> methods = (List<? extends GroovyMethodResult>)getAllCandidates(METHOD);
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
        List<? extends GroovyMethodResult> validMethods = valid(methods);
        if (!validMethods.isEmpty()) {
          return chooseOverloads(validMethods, this);
        }
      }
    }
    else if (!hasAnyMethods && hasAnyProperties && !hasAnyFields) {
      return singleOrValid(properties);
    }
    else if (!hasAnyMethods && !hasAnyProperties && hasAnyFields) {
      return singleOrValid(fields);
    }

    final List<? extends GroovyMethodResult> validMethods = valid(methods);
    if (!validMethods.isEmpty()) {
      return chooseOverloads(validMethods, this);
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
  protected List<? extends GroovyResolveResult> getAllCandidates() {
    for (GroovyResolveKind kind : myAcceptableKinds) {
      List<? extends GroovyResolveResult> results = getAllCandidates(kind);
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

  @Nullable
  @Override
  public PsiType[] getArgumentTypes() {
    return myArgumentTypes.getValue();
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
