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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.plugins.groovy.caches.project.GroovyCachesManager;

/**
 * @author ven
 */
class GroovyDirectInheritorsSearcher implements QueryExecutor<PsiClass, DirectClassInheritorsSearch.SearchParameters> {
  public GroovyDirectInheritorsSearcher() {
  }

  public boolean execute(DirectClassInheritorsSearch.SearchParameters queryParameters, final Processor<PsiClass> consumer) {
    final PsiClass clazz = queryParameters.getClassToProcess();
    final SearchScope scope = queryParameters.getScope();
    if (scope instanceof GlobalSearchScope) {
      final GroovyCachesManager cachesManager = GroovyCachesManager.getInstance(clazz.getProject());
      return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        public Boolean compute() {
          final PsiClass[] candidates = cachesManager.getDeriverCandidates(clazz, (GlobalSearchScope) scope);
          for (PsiClass candidate : candidates) {
            if (candidate.isInheritor(clazz, false)) {
              if (!consumer.process(candidate)) return false;
            }
          }

          return true;
        }
      });
    }

    return true;
  }
}
