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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrSourceFilterScope;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author ven
 */
public class MethodLateBoundReferencesSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {

  public MethodLateBoundReferencesSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull MethodReferencesSearch.SearchParameters queryParameters, @NotNull Processor<PsiReference> consumer) {
    final PsiMethod method = queryParameters.getMethod();
    SearchScope searchScope = GroovyScopeUtil.restrictScopeToGroovyFiles(queryParameters.getScope()).intersectWith(getUseScope(method));
    PsiClass aClass = method.getContainingClass();
    String className = aClass == null ? null : aClass.getName();
    orderSearching(searchScope, method.getName(), method, queryParameters.getOptimizer(), method.getParameterList().getParametersCount());

    final String propName = PropertyUtil.getPropertyName(method);
    if (propName != null) {
      orderSearching(searchScope, propName, method, queryParameters.getOptimizer(), -1);
    }
  }

  private static SearchScope getUseScope(final PsiMethod method) {
    final SearchScope scope = method.getUseScope();
    final PsiFile file = method.getContainingFile();
    if (file != null && scope instanceof GlobalSearchScope) {
      final VirtualFile vfile = file.getOriginalFile().getVirtualFile();
      final Project project = method.getProject();
      if (vfile != null && ProjectRootManager.getInstance(project).getFileIndex().isInSource(vfile)) {
        return new GrSourceFilterScope((GlobalSearchScope)scope);
      }
    }
    return scope;
  }


  private static void orderSearching(SearchScope searchScope,
                                     final String name,
                                     @NotNull PsiMethod searchTarget,
                                     @NotNull SearchRequestCollector collector,
                                     final int paramCount) {
    if (StringUtil.isEmpty(name)) return;
    collector.searchWord(name, searchScope, UsageSearchContext.IN_CODE, true, searchTarget, new RequestResultProcessor("groovy.lateBound") {
      @Override
      public boolean processTextOccurrence(@NotNull PsiElement element, int offsetInElement, @NotNull Processor<PsiReference> consumer) {
        if (!(element instanceof GrReferenceExpression)) {
          return true;
        }

        final GrReferenceExpression ref = (GrReferenceExpression)element;
        if (!name.equals(ref.getReferenceName()) || PsiUtil.isLValue(ref) || ref.resolve() != null) {
          return true;
        }

        PsiElement parent = ref.getParent();
        if (parent instanceof GrMethodCall) {
          if (!argumentsMatch((GrMethodCall)parent, paramCount)) {
            return true;
          }
        } else if (ResolveUtil.isKeyOfMap(ref)) {
          return true;
        }

        return consumer.process((PsiReference)element);
      }
    });
  }

  private static boolean argumentsMatch(GrMethodCall call, int paramCount) {
    int argCount = call.getExpressionArguments().length;
    if (PsiImplUtil.hasNamedArguments(call.getArgumentList())) {
      argCount++;
    }
    return argCount == paramCount;
  }
}