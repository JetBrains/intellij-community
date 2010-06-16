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
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchRequestor;
import com.intellij.psi.search.UsageSearchContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * author ven
 */
public class AccessorReferencesSearcher extends SearchRequestor {
  @Override
  public void contributeRequests(@NotNull PsiElement target,
                                      @NotNull final FindUsagesOptions options,
                                      @NotNull SearchRequestCollector collector) {
    if (!(target instanceof PsiMethod)) {
      return;
    }

    final String propertyName = GroovyPropertyUtils.getPropertyName((PsiMethod)target);
    if (propertyName != null) {
      collector.searchWord(propertyName, PsiUtil.restrictScopeToGroovyFiles(options.searchScope), UsageSearchContext.IN_CODE, true, target);
    }
  }

}
