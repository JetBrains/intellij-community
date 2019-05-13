/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.completion.weighers;

import com.intellij.codeInsight.completion.CompletionLocation;
import com.intellij.codeInsight.completion.CompletionWeigher;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;

/**
 * Prefers elements that were added by 'with' closure: foo.with{ caret }
 *
 * @author Max Medvedev
 */
public class GrWithWeigher extends CompletionWeigher {

  @Override
  public Comparable weigh(@NotNull LookupElement element, @NotNull CompletionLocation location) {
    final PsiElement position = location.getCompletionParameters().getPosition();
    if (position.getLanguage() == GroovyLanguage.INSTANCE) return null;

    if (!(position.getParent() instanceof GrReferenceExpression)) return null;

    Object o = element.getObject();
    if (!(o instanceof GroovyResolveResult)) return 0;

    final PsiElement resolveContext = ((GroovyResolveResult)o).getCurrentFileResolveContext();

    if (resolveContext == null) return 0;

    if (GdkMethodUtil.isInWithContext(resolveContext)) {
      return 1;
    }

    return 0;
  }
}
