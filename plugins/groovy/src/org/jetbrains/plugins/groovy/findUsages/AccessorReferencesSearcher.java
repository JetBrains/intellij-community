/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

/**
 * author ven
 */
public class AccessorReferencesSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {

  protected AccessorReferencesSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull Processor<PsiReference> consumer) {
    final PsiElement element = queryParameters.getElementToSearch();
    if (element instanceof PsiMethod) {
      final String propertyName = GroovyPropertyUtils.getPropertyName((PsiMethod)element);
      if (propertyName == null) return;

      queryParameters.getOptimizer().searchWord(propertyName, GroovyScopeUtil
        .restrictScopeToGroovyFiles(queryParameters.getEffectiveSearchScope()),
                                                UsageSearchContext.IN_CODE, true, element);
    }
    else if (element instanceof GrField) {
      for (GrAccessorMethod method : ((GrField)element).getGetters()) {
        MethodReferencesSearch.search(method, queryParameters.getEffectiveSearchScope(), true).forEach(consumer);
      }

      final GrAccessorMethod setter = ((GrField)element).getSetter();
      if (setter != null) {
        MethodReferencesSearch.search(setter, queryParameters.getEffectiveSearchScope(), true).forEach(consumer);
      }
    }
  }
}
