// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.EnumSet;

import static org.jetbrains.plugins.groovy.lang.resolve.processors.GroovyResolveKind.*;

public final class GroovyResolverProcessorBuilder {

  private boolean myIncomplete = false;
  private boolean myAllVariants = false;
  private boolean myForceRValue = false;

  @NotNull
  public GroovyResolverProcessor build(GrReferenceExpression ref) {
    final EnumSet<GroovyResolveKind> kinds = myIncomplete ? EnumSet.allOf(GroovyResolveKind.class) : computeKinds(ref);
    if (myAllVariants) {
      return new GroovyAllVariantsProcessor(ref, kinds);
    }
    else {
      return new GroovyResolverProcessorImpl(ref, kinds, myForceRValue);
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

  @NotNull
  public GroovyResolverProcessorBuilder setAllVariants(boolean allVariants) {
    myAllVariants = allVariants;
    return this;
  }

  @NotNull
  public GroovyResolverProcessorBuilder setForceRValue(boolean forceRValue) {
    myForceRValue = forceRValue;
    return this;
  }

  @NotNull
  private static EnumSet<GroovyResolveKind> computeKinds(@NotNull GrReferenceExpression ref) {
    if (ref.hasAt()) return EnumSet.of(FIELD);
    assert !ref.hasMemberPointer();

    final EnumSet<GroovyResolveKind> result = EnumSet.allOf(GroovyResolveKind.class);
    result.remove(CLASS);
    result.remove(PACKAGE);

    if (ref.isQualified()) result.remove(BINDING);
    if (!(ref.getParent() instanceof GrMethodCall)) result.remove(METHOD);

    return result;
  }
}
