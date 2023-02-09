// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.search.GrSourceFilterScope;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

public class MethodLateBoundReferencesSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {

  public MethodLateBoundReferencesSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull MethodReferencesSearch.SearchParameters queryParameters, @NotNull Processor<? super PsiReference> consumer) {
    final PsiMethod method = queryParameters.getMethod();
    SearchScope searchScope = GroovyScopeUtil.restrictScopeToGroovyFiles(queryParameters.getEffectiveSearchScope()).intersectWith(
      getUseScope(method));
    orderSearching(searchScope, method.getName(), method, queryParameters.getOptimizer(), method.getParameterList().getParametersCount());

    final String propName = PropertyUtilBase.getPropertyName(method);
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
      public boolean processTextOccurrence(@NotNull PsiElement element, int offsetInElement, @NotNull Processor<? super PsiReference> consumer) {
        if (!(element instanceof GrReferenceExpression ref)) {
          return true;
        }

        if (!name.equals(ref.getReferenceName()) || PsiUtil.isLValue(ref)) {
          return true;
        }

        PsiElement parent = ref.getParent();
        if (parent instanceof GrCommandArgumentList) {
          parent = parent.getParent();
        }
        if (paramCount >= 0 && !ref.hasMemberPointer() &&
            (!(parent instanceof GrMethodCall) || !argumentsMatch((GrMethodCall)parent, paramCount))) {
          return true;
        }

        GrExpression qualifier = ref.getQualifierExpression();
        if (qualifier == null || qualifier.getType() != null) {
          return true;
        }

        if (ref.resolve() != null) {
          return true;
        }

        if (ResolveUtil.isKeyOfMap(ref)) {
          return true;
        }

        return consumer.process((PsiReference)element);
      }
    });
  }

  private static boolean argumentsMatch(GrMethodCall call, int paramCount) {
    int argCount = call.getExpressionArguments().length + call.getClosureArguments().length;
    if (PsiImplUtil.hasNamedArguments(call.getArgumentList())) {
      argCount++;
    }
    return argCount == paramCount;
  }
}
