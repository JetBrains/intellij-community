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

import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

/**
 * @author Maxim.Medvedev
 */
public class GroovyConstructorUsagesSearcher implements QueryExecutor<PsiReference, MethodReferencesSearch.SearchParameters> {
  public boolean execute(MethodReferencesSearch.SearchParameters p, final Processor<PsiReference> consumer) {
    return GroovyConstructorUsagesSearchHelper.processConstructorUsages(p.getMethod(), p.getScope(), consumer, p.getOptimizer());
  }
}
