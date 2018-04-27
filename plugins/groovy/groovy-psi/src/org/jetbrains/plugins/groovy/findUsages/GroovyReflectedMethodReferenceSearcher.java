// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;

/**
 * @author Max Medvedev
 */
public class GroovyReflectedMethodReferenceSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {
  public GroovyReflectedMethodReferenceSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull MethodReferencesSearch.SearchParameters queryParameters, @NotNull Processor<? super PsiReference> consumer) {
    final PsiMethod method = queryParameters.getMethod();
    if (method instanceof GrMethod) {
      for (GrReflectedMethod reflectedMethod : ((GrMethod)method).getReflectedMethods()) {
        MethodReferencesSearch.search(reflectedMethod, queryParameters.getEffectiveSearchScope(), true).forEach(consumer);
      }
    }
  }
}
