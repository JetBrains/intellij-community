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
package org.jetbrains.plugins.groovy.lang.psi.impl;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrAnonymousClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrAnonymousClassIndex;
import org.jetbrains.plugins.groovy.lang.psi.stubs.index.GrDirectInheritorsIndex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class GroovyDirectInheritorsSearcher implements QueryExecutor<PsiClass, DirectClassInheritorsSearch.SearchParameters> {

  @NotNull
  private static List<PsiClass> getDerivingClassCandidates(PsiClass clazz, GlobalSearchScope scope, boolean includeAnonymous) {
    final String name = clazz.getName();
    if (name == null) return Collections.emptyList();
    final ArrayList<PsiClass> inheritors = new ArrayList<PsiClass>();
    for (GrReferenceList list : StubIndex.getElements(GrDirectInheritorsIndex.KEY, name, clazz.getProject(), scope,
                                                      GrReferenceList.class)) {
      final PsiElement parent = list.getParent();
      if (parent instanceof GrTypeDefinition) {
        inheritors.add((PsiClass)parent);
      }
    }
    if (includeAnonymous) {
      final Collection<GrAnonymousClassDefinition> classes =
        StubIndex.getElements(GrAnonymousClassIndex.KEY, name, clazz.getProject(), scope, GrAnonymousClassDefinition.class);
      for (GrAnonymousClassDefinition aClass : classes) {
        inheritors.add(aClass);
      }
    }
    return inheritors;
  }

  @Override
  public boolean execute(@NotNull final DirectClassInheritorsSearch.SearchParameters queryParameters, @NotNull final Processor<PsiClass> consumer) {
    final PsiClass clazz = queryParameters.getClassToProcess();
    final SearchScope scope = queryParameters.getScope();
    if (scope instanceof GlobalSearchScope) {
      final List<PsiClass> candidates = ApplicationManager.getApplication().runReadAction(new Computable<List<PsiClass>>() {
        @Override
        public List<PsiClass> compute() {
          if (!clazz.isValid()) return Collections.emptyList();
          return getDerivingClassCandidates(clazz, (GlobalSearchScope)scope, queryParameters.includeAnonymous());
        }
      });
      for (final PsiClass candidate : candidates) {
        if (!queryParameters.isCheckInheritance() || isInheritor(clazz, candidate)) {
          if (!consumer.process(candidate)) {
            return false;
          }
        }
      }

      return true;
    }

    return true;
  }

  private static boolean isInheritor(PsiClass clazz, PsiClass candidate) {
    AccessToken accessToken = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      return candidate.isValid() && candidate.isInheritor(clazz, false);
    }
    finally {
      accessToken.finish();
    }
  }
}
