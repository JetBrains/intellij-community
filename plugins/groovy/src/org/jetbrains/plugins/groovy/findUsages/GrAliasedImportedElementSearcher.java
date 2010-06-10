/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NullableComputable;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Maxim.Medvedev
 */
public class GrAliasedImportedElementSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  private static final Key<Boolean> SEARCHING_FOR_IT = Key.create("SEARCHING_FOR_IT");

  public boolean execute(final ReferencesSearch.SearchParameters parameters, final Processor<PsiReference> consumer) {
    final PsiElement elementToSearch = parameters.getElementToSearch();
    if (!(elementToSearch instanceof PsiMember)) return true;
    if (elementToSearch.getUserData(SEARCHING_FOR_IT) != null) return true;
    try {
      elementToSearch.putUserData(SEARCHING_FOR_IT, Boolean.TRUE);
      final TextOccurenceProcessor processor = new TextOccurenceProcessor() {
        public boolean execute(PsiElement element, int offsetInElement) {
          final PsiReference[] refs = element.getParent().getReferences();
          for (PsiReference ref : refs) {
            if (ReferenceRange.containsOffsetInElement(ref, offsetInElement)) {
              if (ref.isReferenceTo(elementToSearch)) {
                return consumer.process(ref);
              }
            }
          }
          return true;
        }
      };
      final PsiSearchHelper helper = PsiManager.getInstance(elementToSearch.getProject()).getSearchHelper();

      final SearchScope groovySearchScope = PsiUtil.restrictScopeToGroovyFiles(new Computable<SearchScope>() {
        public SearchScope compute() {
          return parameters.getEffectiveSearchScope();
        }
      });

      if (!ReferencesSearch.search(elementToSearch, groovySearchScope).forEach(new Processor<PsiReference>() {
        public boolean process(PsiReference reference) {
          final PsiElement element = reference.getElement();
          String alias = ApplicationManager.getApplication().runReadAction(new NullableComputable<String>() {
            public String compute() {
              if (!(element.getParent() instanceof GrImportStatement)) return null;
              final GrImportStatement importStatement = (GrImportStatement)element.getParent();
              if (!importStatement.isAliasedImport()) return null;
              return importStatement.getImportedName();
            }
          });
          if (alias == null) return true;

          final PsiFile containingFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
            public PsiFile compute() {
              return element.getContainingFile();
            }
          });

          return helper
            .processElementsWithWord(processor, GlobalSearchScope.fileScope(containingFile), alias, UsageSearchContext.IN_CODE, false);
        }
      })) return false;

      if (elementToSearch instanceof PsiMethod && GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)elementToSearch)) {
        final PsiField field = GroovyPropertyUtils.findFieldForAccessor((PsiMethod)elementToSearch, true);
        if (field != null) {
          try {
            field.putUserData(SEARCHING_FOR_IT, Boolean.TRUE);
            if (!ReferencesSearch.search(field, groovySearchScope).forEach(new Processor<PsiReference>() {
              public boolean process(PsiReference psiReference) {
                final PsiElement element = psiReference.getElement();
                String alias = ApplicationManager.getApplication().runReadAction(new NullableComputable<String>() {
                  public String compute() {
                    if (!(element.getParent() instanceof GrImportStatement)) return null;
                    final GrImportStatement importStatement = (GrImportStatement)element.getParent();
                    if (!importStatement.isAliasedImport()) return null;
                    return importStatement.getImportedName();
                  }
                });
                if (alias == null) return true;
                final String prefix = GroovyPropertyUtils.getAccessorPrefix((PsiMethod)elementToSearch);
                if (prefix == null) return true;

                alias = prefix + alias;
                final PsiFile containingFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
                  public PsiFile compute() {
                    return element.getContainingFile();
                  }
                });

                return helper
                  .processElementsWithWord(processor, GlobalSearchScope.fileScope(containingFile), alias, UsageSearchContext.IN_CODE,
                                           false);
              }
            })) {
              return false;
            }
          }
          finally {
            field.putUserData(SEARCHING_FOR_IT, null);
          }
        }
      }
      return true;
    }
    finally {
      elementToSearch.putUserData(SEARCHING_FOR_IT, null);
    }
  }
}
