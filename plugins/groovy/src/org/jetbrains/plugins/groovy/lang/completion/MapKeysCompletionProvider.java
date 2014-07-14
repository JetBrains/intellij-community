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
package org.jetbrains.plugins.groovy.lang.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyMapCompletionUtil;
import org.jetbrains.plugins.groovy.extensions.GroovyMapContentProvider;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;

/**
 * @author Sergey Evdokimov
 */
class MapKeysCompletionProvider extends CompletionProvider<CompletionParameters> {

  public static void register(CompletionContributor contributor) {
    MapKeysCompletionProvider provider = new MapKeysCompletionProvider();

    contributor.extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(PlatformPatterns.psiElement(GrReferenceExpression.class)), provider);
  }

  @Override
  protected void addCompletions(@NotNull CompletionParameters parameters,
                                ProcessingContext context,
                                @NotNull CompletionResultSet result) {
    PsiElement element = parameters.getPosition();
    GrReferenceExpression expression = (GrReferenceExpression)element.getParent();

    GrExpression qualifierExpression = expression.getQualifierExpression();
    if (qualifierExpression == null) return;

    PsiType mapType = qualifierExpression.getType();

    if (!GroovyPsiManager.isInheritorCached(mapType, CommonClassNames.JAVA_UTIL_MAP)) {
      return;
    }

    PsiElement resolve = null;

    if (qualifierExpression instanceof GrMethodCall) {
      resolve = ((GrMethodCall)qualifierExpression).resolveMethod();
    }
    else if (qualifierExpression instanceof GrReferenceExpression) {
      resolve = ((GrReferenceExpression)qualifierExpression).resolve();
    }

    for (GroovyMapContentProvider provider : GroovyMapContentProvider.EP_NAME.getExtensions()) {
      GroovyMapCompletionUtil.addKeyVariants(provider, qualifierExpression, resolve, result);
    }

    if (mapType instanceof GrMapType) {
      for (String key : ((GrMapType)mapType).getStringKeys()) {
        LookupElement lookup = LookupElementBuilder.create(key);
        lookup = PrioritizedLookupElement.withPriority(lookup, 1);
        result.addElement(lookup);
      }
    }
  }
}
