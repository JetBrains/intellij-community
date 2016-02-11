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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.EnumSet;

public final class GroovyResolverProcessorBuilder {

  private boolean myIncomplete = false;
  private boolean myAllVariants = false;
  private GrExpression myUpToArgument = null;

  @NotNull
  public GroovyResolverProcessor build(GrReferenceExpression ref) {
    final EnumSet<GroovyResolveKind> kinds = myIncomplete ? EnumSet.allOf(GroovyResolveKind.class) : computeKinds(ref);
    if (myAllVariants) {
      return new GroovyAllVariantsProcessor(ref, kinds, myUpToArgument);
    }
    else {
      return new GroovyResolverProcessorImpl(ref, kinds);
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
  public GroovyResolverProcessorBuilder setUpToArgument(GrExpression upToArgument) {
    myUpToArgument = upToArgument;
    return this;
  }

  @NotNull
  private static EnumSet<GroovyResolveKind> computeKinds(@NotNull GrReferenceExpression ref) {
    if (ref.hasAt()) return EnumSet.of(GroovyResolveKind.FIELD);
    if (ref.hasMemberPointer()) return EnumSet.of(GroovyResolveKind.METHOD);

    final EnumSet<GroovyResolveKind> result = EnumSet.allOf(GroovyResolveKind.class);

    if (!ResolveUtil.canBeClass(ref)) result.remove(GroovyResolveKind.CLASS);
    if (!ResolveUtil.canBePackage(ref)) result.remove(GroovyResolveKind.PACKAGE);
    if (ref.isQualified()) result.remove(GroovyResolveKind.BINDING);
    if (!(ref.getParent() instanceof GrMethodCall)) result.remove(GroovyResolveKind.METHOD);

    return result;
  }
}
