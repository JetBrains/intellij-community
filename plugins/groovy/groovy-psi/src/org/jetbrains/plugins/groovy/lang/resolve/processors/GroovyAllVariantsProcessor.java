// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.EnumSet;
import java.util.List;

class GroovyAllVariantsProcessor extends GroovyResolverProcessor {

  GroovyAllVariantsProcessor(@NotNull GrReferenceExpression ref,
                             @NotNull EnumSet<GroovyResolveKind> kinds) {
    super(ref, kinds, false);
  }

  @NotNull
  @Override
  public List<GroovyResolveResult> getCandidates() {
    final List<GroovyResolveResult> results = ContainerUtil.newArrayList();
    results.addAll(myCandidates.values());
    myAccessorProcessors.forEach(it -> results.addAll(it.getResults()));
    results.addAll(myInapplicableCandidates.values());
    return ContainerUtil.newArrayList(ResolveUtil.filterSameSignatureCandidates(results));
  }
}
