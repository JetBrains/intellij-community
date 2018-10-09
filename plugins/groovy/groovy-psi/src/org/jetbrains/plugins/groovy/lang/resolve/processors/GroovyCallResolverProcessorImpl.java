// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.EnumSet;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.newSmartList;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.singleOrValid;
import static org.jetbrains.plugins.groovy.lang.resolve.ResolveUtilKt.valid;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolveKind.*;

class GroovyCallResolverProcessorImpl extends GroovyResolverProcessorImpl {

  GroovyCallResolverProcessorImpl(@NotNull GrReferenceExpression ref, @NotNull EnumSet<GroovyResolveKind> kinds, boolean forceRValue) {
    super(ref, kinds, forceRValue);
  }

  @Override
  @NotNull
  public List<GroovyResolveResult> getCandidates() {
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
}
