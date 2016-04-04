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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrBindingVariable;

import java.util.List;

import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.RESOLVE_KINDS_PROPERTY;

/**
 * @author ven
 */
public class PropertyResolverProcessor extends ResolverProcessorImpl {

  public PropertyResolverProcessor(String name, PsiElement place) {
    super(name, RESOLVE_KINDS_PROPERTY, place, PsiType.EMPTY_ARRAY);
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
    return super.execute(element, state) || element instanceof PsiField;
  }

  @NotNull
  @Override
  public GroovyResolveResult[] getCandidates() {
    //do not have more than one correct result. And if it exists it is the last
    final List<GroovyResolveResult> candidates = getCandidatesInternal();
    final int size = candidates.size();
    if (size == 0) return GroovyResolveResult.EMPTY_ARRAY;

    GroovyResolveResult last = candidates.get(size - 1);
    if (last.getElement() instanceof GrBindingVariable && size > 1) {
      last = candidates.get(size - 2);
    }
    if (isCorrectLocalVarOrParam(last)) {
      return new GroovyResolveResult[]{last};
    }
    for (GroovyResolveResult candidate : candidates) {
      if (candidate.isStaticsOK()) {
        return new GroovyResolveResult[]{candidate};
      }
    }
    return candidates.toArray(new GroovyResolveResult[candidates.size()]);
  }

  private static boolean isCorrectLocalVarOrParam(GroovyResolveResult last) {
    return !(last.getElement() instanceof PsiField) &&
           last.isAccessible() &&
           last.isStaticsOK() &&
           last.getCurrentFileResolveContext() == null;
  }
}
