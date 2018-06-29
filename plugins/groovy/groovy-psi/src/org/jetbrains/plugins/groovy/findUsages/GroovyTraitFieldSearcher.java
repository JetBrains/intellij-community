// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.*;
import com.intellij.psi.search.RequestResultProcessor;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitField;
import org.jetbrains.plugins.groovy.lang.psi.util.GrTraitUtil;

public class GroovyTraitFieldSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  public GroovyTraitFieldSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters p, @NotNull Processor<? super PsiReference> consumer) {
    final PsiElement target = p.getElementToSearch();

    if (target instanceof GrField && !(target instanceof GrTraitField)) {
      PsiClass aClass = ((GrField)target).getContainingClass();
      if (GrTraitUtil.isTrait(aClass)) {
        String traitFieldName = GrTraitUtil.getTraitFieldPrefix(aClass) + ((GrField)target).getName();

        p.getOptimizer().searchWord(traitFieldName, p.getEffectiveSearchScope(), UsageSearchContext.IN_CODE, true, target, new MyProcessor(target));
      }
    }
  }

  private static class MyProcessor extends RequestResultProcessor {
    private final PsiElement myTarget;
    private final PsiManager myManager;

    public MyProcessor(PsiElement target) {
      myTarget = target;
      myManager = myTarget.getManager();
    }

    @Override
    public boolean processTextOccurrence(@NotNull PsiElement element,
                                         int offsetInElement,
                                         @NotNull Processor<? super PsiReference> consumer) {
      PsiElement parent = element.getParent();
      if (parent instanceof GrReferenceExpression && element == ((GrReferenceExpression)parent).getReferenceNameElement()) {
        PsiElement resolved = ((GrReferenceExpression)parent).resolve();
        if (resolved instanceof GrTraitField) {
          PsiField prototype = ((GrTraitField)resolved).getPrototype();
          if (myManager.areElementsEquivalent(prototype, resolved)) {
            if (!consumer.process((PsiReference)parent)) {
              return false;
            }
          }
        }
      }
      return true;
    }
  }
}
