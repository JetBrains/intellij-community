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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.EnumSet;

import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.RESOLVE_KINDS_CLASS_PACKAGE;
import static org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint.RESOLVE_KINDS_METHOD_PROPERTY;

/**
 * @author ven
 */
public class CompletionProcessor extends ResolverProcessorImpl {
  private CompletionProcessor(PsiElement place, final EnumSet<DeclarationKind> resolveTargets, final String name) {
    super(name, resolveTargets, place, PsiType.EMPTY_ARRAY);
  }

  @Override
  public boolean execute(@NotNull PsiElement element, @NotNull ResolveState substitutor) {
    if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
      return true;
    }
    super.execute(element, substitutor);
    return true;
  }

  public static ResolverProcessor createPropertyCompletionProcessor(PsiElement place) {
    return new CompletionProcessor(place, RESOLVE_KINDS_METHOD_PROPERTY, null);
  }

  public static ResolverProcessor createRefSameNameProcessor(PsiElement place, String name) {
    return new CompletionProcessor(place, RESOLVE_KINDS_METHOD_PROPERTY, name);
  }

  public static ResolverProcessor createClassCompletionProcessor(PsiElement place) {
    return new CompletionProcessor(place, RESOLVE_KINDS_CLASS_PACKAGE, null);
  }

  @Override
  @NotNull
  public GroovyResolveResult[] getCandidates() {
    if (!super.hasCandidates()) return GroovyResolveResult.EMPTY_ARRAY;
    return ResolveUtil.filterSameSignatureCandidates(getCandidatesInternal());
  }
}
