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

package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * author ven
 */
public class AccessorReferencesSearcher implements QueryExecutor<PsiReference, MethodReferencesSearch.SearchParameters> {

  public boolean execute(final MethodReferencesSearch.SearchParameters searchParameters, final Processor<PsiReference> consumer) {
    final PsiMethod method = searchParameters.getMethod();
    final String propertyName = getPropertyName(method);
    if (propertyName == null) return true;

    SearchScope searchScope = PsiUtil.restrictScopeToGroovyFiles(new Computable<SearchScope>() {
      public SearchScope compute() {
        return searchParameters.getScope();
      }
    });

    final PsiSearchHelper helper = PsiManager.getInstance(method.getProject()).getSearchHelper();
    final TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        final PsiReference[] refs = element.getReferences();
        for (PsiReference ref : refs) {
          if (ReferenceRange.containsOffsetInElement(ref, offsetInElement)) {
            if (ref.isReferenceTo(method)) {
              return consumer.process(ref);
            }
          }
        }
        return true;
      }
    };

    return helper.processElementsWithWord(processor, searchScope, propertyName, UsageSearchContext.IN_CODE, false);
  }

  @Nullable
  private static String getPropertyName(final PsiMethod method) {
    return ApplicationManager.getApplication().runReadAction(new NullableComputable<String>() {
      @Nullable
      public String compute() {
        return GroovyPropertyUtils.getPropertyName(method);
      }
    });
  }
}
