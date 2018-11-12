// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.GrResolverProcessor;

import java.util.Objects;

public final class GroovyResolverProcessorBuilder {

  private boolean myIncomplete = false;

  @NotNull
  public GrResolverProcessor<GroovyResolveResult> build(GrReferenceExpression ref) {
    if (myIncomplete) {
      return new AllVariantsProcessor(Objects.requireNonNull(ref.getReferenceName()), ref);
    }
    else {
      throw new IllegalStateException();
    }
  }

  @NotNull
  public static GroovyResolverProcessorBuilder builder() {
    return new GroovyResolverProcessorBuilder();
  }

  public GroovyResolverProcessorBuilder setIncomplete(boolean incomplete) {
    myIncomplete = incomplete;
    return this;
  }
}
