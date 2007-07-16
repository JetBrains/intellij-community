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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ven
 */
public class MethodLateBoundReferencesSearcher implements QueryExecutor<PsiReference, MethodReferencesSearch.SearchParameters> {
  public boolean execute(final MethodReferencesSearch.SearchParameters params, final Processor<PsiReference> consumer) {
    final PsiElement element = params.getMethod();
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) element;
      SearchScope searchScope = PsiUtil.restrictScopeToGroovyFiles(params.getScope().intersectWith(getUseScopeInReadAction(method)));

      final String name = method.getName();

      final Project project = element.getProject();
      if (!processTextOccurrences(searchScope, name, consumer, project)) return false;

      final String propName = getPropertyName(method);
      if (propName != null) {
        if (!processTextOccurrences(searchScope, propName, consumer, project)) return false;
      }
    }
    return true;
  }

  private SearchScope getUseScopeInReadAction(final PsiMethod method) {
    return ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      public SearchScope compute() {
        return method.getUseScope();
      }
    });
  }

  private String getPropertyName(final PsiMethod method) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>(){
      public String compute() {
        return PropertyUtil.getPropertyName(method);
      }
    });
  }

  private boolean processTextOccurrences(SearchScope searchScope, final String name, final Processor<PsiReference> consumer, Project project) {
    final TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        if (element instanceof GrReferenceExpression &&
            name.equals(((GrReferenceExpression) element).getReferenceName()) &&
            ((GrReferenceExpression) element).resolve() == null &&
            !PsiUtil.isLValue((GroovyPsiElement) element)) {
          if (!consumer.process((PsiReference) element)) return false;
        }
        return true;
      }
    };

    return PsiManager.getInstance(project).getSearchHelper().processElementsWithWord(processor,
        searchScope, name, UsageSearchContext.IN_CODE, true);
  }

}