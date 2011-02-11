/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;

import java.util.List;

/**
 * @author ven
 */
public class PropertyResolverProcessor extends ResolverProcessor {

  public PropertyResolverProcessor(String name, PsiElement place) {
    super(name, RESOLVE_KINDS_PROPERTY, place, PsiType.EMPTY_ARRAY);
  }

  @Override
  public boolean execute(PsiElement element, ResolveState state) {
    if (element instanceof GrReferenceExpression && ((GrReferenceExpression)element).getQualifier()!=null) {
      return true;
    }
    return super.execute(element, state);
  }

  @NotNull
  @Override
  public GroovyResolveResult[] getCandidates() {
    //do not have more than one correct result. And if it exists it is the last
    final List<GroovyResolveResult> candidates = getCandidatesInternal();
    final int size = candidates.size();
    if (size == 0) return GroovyResolveResult.EMPTY_ARRAY;
    final GroovyResolveResult last = candidates.get(size - 1);
    if (last.isAccessible() && last.isStaticsOK()) return candidates.toArray(new GroovyResolveResult[candidates.size()]);
    for (GroovyResolveResult candidate : candidates) {
      if (candidate.isStaticsOK()) return new GroovyResolveResult[]{candidate};
    }
    return candidates.toArray(new GroovyResolveResult[candidates.size()]);
  }
}
