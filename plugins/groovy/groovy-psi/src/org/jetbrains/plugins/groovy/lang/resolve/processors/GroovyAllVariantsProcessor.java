/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.EnumSet;
import java.util.List;

class GroovyAllVariantsProcessor extends GroovyResolverProcessor {

  GroovyAllVariantsProcessor(@NotNull GrReferenceExpression ref,
                             @NotNull EnumSet<GroovyResolveKind> kinds,
                             @Nullable GrExpression myUpToArgument) {
    super(ref, kinds, myUpToArgument);
  }

  @NotNull
  @Override
  public List<GroovyResolveResult> getCandidates() {
    final List<GroovyResolveResult> results = ContainerUtil.newArrayList();
    results.addAll(myCandidates.values());
    results.addAll(myInapplicableCandidates.values());
    return ContainerUtil.newArrayList(ResolveUtil.filterSameSignatureCandidates(results));
  }
}
