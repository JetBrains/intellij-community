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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrSourceFilterScope;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ven
 */
public class MethodLateBoundReferencesSearcher implements QueryExecutor<PsiReference, MethodReferencesSearch.SearchParameters> {
  public boolean execute(final MethodReferencesSearch.SearchParameters params, final Processor<PsiReference> consumer) {
    final PsiMethod method = params.getMethod();
    SearchScope searchScope = PsiUtil.restrictScopeToGroovyFiles(new Computable<SearchScope>() {
      public SearchScope compute() {
        return params.getScope().intersectWith(getUseScope(method));
      }
    });

    final String name = method.getName();

    final Project project = method.getProject();
    if (!processTextOccurrences(searchScope, name, consumer, project)) return false;

    final String propName = getPropertyName(method);
    if (propName != null) {
      if (!processTextOccurrences(searchScope, propName, consumer, project)) return false;
    }
    return true;
  }

  private static SearchScope getUseScope(final PsiMethod method) {
    final SearchScope scope = method.getUseScope();
    final PsiFile file = method.getContainingFile();
    if (file != null && scope instanceof GlobalSearchScope) {
      final VirtualFile vfile = file.getOriginalFile().getVirtualFile();
      final Project project = method.getProject();
      if (vfile != null && ProjectRootManager.getInstance(project).getFileIndex().isInSource(vfile)) {
        return new GrSourceFilterScope((GlobalSearchScope)scope, project);
      }
    }
    return scope;
  }

  private static String getPropertyName(final PsiMethod method) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String>(){
      @Nullable
      public String compute() {
        return PropertyUtil.getPropertyName(method);
      }
    });
  }

  private static boolean processTextOccurrences(SearchScope searchScope, final String name, final Processor<PsiReference> consumer, Project project) {
    final TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        if (!(element instanceof GrReferenceExpression)) {
          return true;
        }

        final GrReferenceExpression ref = (GrReferenceExpression)element;
        if (!name.equals(ref.getReferenceName()) || PsiUtil.isLValue(ref) || ref.resolve() != null) {
          return true;
        }

        return consumer.process((PsiReference)element);
      }
    };

    return PsiManager.getInstance(project).getSearchHelper().processElementsWithWord(processor,
                                                                                     searchScope, name, UsageSearchContext.IN_CODE, true);
  }

}