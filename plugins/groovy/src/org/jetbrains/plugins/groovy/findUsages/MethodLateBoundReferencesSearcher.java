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

import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.*;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrSourceFilterScope;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author ven
 */
public class MethodLateBoundReferencesSearcher extends SearchRequestor {
  @Override
  public void contributeSearchTargets(@NotNull PsiElement target,
                                      @NotNull FindUsagesOptions options,
                                      @NotNull PsiSearchRequest.ComplexRequest collector,
                                      Processor<PsiReference> consumer) {
    if (!(target instanceof PsiMethod)) {
      return;
    }

    final PsiMethod method = (PsiMethod)target;
    SearchScope searchScope = PsiUtil.restrictScopeToGroovyFiles(options.searchScope.intersectWith(getUseScope(method)));

    orderSearching(searchScope, method.getName(), consumer, collector);

    final String propName = PropertyUtil.getPropertyName(method);
    if (propName != null) {
      orderSearching(searchScope, propName, consumer, collector);
    }
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


  private static void orderSearching(SearchScope searchScope,
                                             final String name,
                                             final Processor<PsiReference> consumer,
                                             @NotNull PsiSearchRequest.ComplexRequest collector) {
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

    collector.addRequest(PsiSearchRequest.elementsWithWord(searchScope, name, UsageSearchContext.IN_CODE, true, processor));
  }

}