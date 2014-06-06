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

/**
 * Created by Max Medvedev on 15/04/14
 */
public class GroovyTraitFieldSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  public GroovyTraitFieldSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters p, @NotNull Processor<PsiReference> consumer) {
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
                                         @NotNull Processor<PsiReference> consumer) {
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
