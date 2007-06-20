/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * author ven
 */
public class AccessorReferencesSearcher implements QueryExecutor<PsiReference, MethodReferencesSearch.SearchParameters> {

  public boolean execute(final MethodReferencesSearch.SearchParameters searchParameters, final Processor<PsiReference> consumer) {
    final PsiMethod method = searchParameters.getMethod();
    if (PropertyUtil.isSimplePropertyAccessor(method)) {
      final String propertyName = PropertyUtil.getPropertyName(method);
      assert propertyName != null;
      SearchScope searchScope = PsiUtil.restrictScopeToGroovyFiles(searchParameters.getScope());

      final PsiSearchHelper helper = PsiManager.getInstance(method.getProject()).getSearchHelper();
      final TextOccurenceProcessor processor = new TextOccurenceProcessor() {
        public boolean execute(PsiElement element, int offsetInElement) {
          final PsiReference[] refs = element.getReferences();
          for (PsiReference ref : refs) {
            if (ref.getRangeInElement().contains(offsetInElement)) {
              if (ref.isReferenceTo(method)) {
                return consumer.process(ref);
              }
            }
          }
          return true;
        }
      };

      if (!helper.processElementsWithWord(processor,
          searchScope,
          propertyName,
          UsageSearchContext.IN_CODE,
          false)) {
        return false;
      }
    }

    return true;
  }
}
