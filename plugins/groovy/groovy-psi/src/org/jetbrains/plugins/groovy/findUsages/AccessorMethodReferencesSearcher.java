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

package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

/**
 * author ven
 */
public class AccessorMethodReferencesSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {

  public AccessorMethodReferencesSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull MethodReferencesSearch.SearchParameters queryParameters, @NotNull Processor<PsiReference> consumer) {
    final PsiMethod method = queryParameters.getMethod();

    final String propertyName;
    if (GdkMethodUtil.isCategoryMethod(method, null, null, PsiSubstitutor.EMPTY)) {
      final GrGdkMethod cat = GrGdkMethodImpl.createGdkMethod(method, false, null);
      propertyName = GroovyPropertyUtils.getPropertyName((PsiMethod)cat);
    }
    else {
      propertyName = GroovyPropertyUtils.getPropertyName(method);
    }

    if (propertyName == null) return;

    final SearchScope onlyGroovyFiles = GroovyScopeUtil.restrictScopeToGroovyFiles(queryParameters.getEffectiveSearchScope(), GroovyScopeUtil.getEffectiveScope(method));

    queryParameters.getOptimizer().searchWord(propertyName, onlyGroovyFiles, UsageSearchContext.IN_CODE, true, method);

    if (!GroovyPropertyUtils.isPropertyName(propertyName)) {
      queryParameters.getOptimizer().searchWord(StringUtil.decapitalize(propertyName), onlyGroovyFiles, UsageSearchContext.IN_CODE, true, method);
    }
  }

}
