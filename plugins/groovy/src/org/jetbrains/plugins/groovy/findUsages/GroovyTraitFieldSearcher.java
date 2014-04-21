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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.resolve.CollectClassMembersUtil;

/**
 * Created by Max Medvedev on 15/04/14
 */
public class GroovyTraitFieldSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters p, @NotNull Processor<PsiReference> consumer) {
    final PsiElement target = p.getElementToSearch();

    String traitFieldName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return getTraitFieldNameOrNull(target);
      }
    });
    if (traitFieldName != null) {
      p.getOptimizer().searchWord(traitFieldName, p.getEffectiveSearchScope(), UsageSearchContext.IN_CODE, true, target);
    }
  }


  @Nullable
  private static String getTraitFieldNameOrNull(@NotNull PsiElement target) {

    if (target instanceof GrField && ((GrField)target).hasModifierProperty(PsiModifier.PUBLIC)) {
      PsiClass aClass = ((GrField)target).getContainingClass();
      if (PsiImplUtil.isTrait(aClass)) {
        String prefix = CollectClassMembersUtil.getTraitFieldPrefix(aClass);

        return prefix + ((GrField)target).getName();
      }
    }

    return null;
  }
}
