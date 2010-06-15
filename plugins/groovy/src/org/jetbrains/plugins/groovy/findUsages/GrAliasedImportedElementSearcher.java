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
import com.intellij.openapi.util.NullableComputable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
 * @author Maxim.Medvedev
 */
public class GrAliasedImportedElementSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  private static final ThreadLocal<Object> I_WAS_HERE = new ThreadLocal<Object>();

  public boolean execute(final ReferencesSearch.SearchParameters parameters, final Processor<PsiReference> consumer) {
    final PsiElement elementToSearch = parameters.getElementToSearch();
    if (!(elementToSearch instanceof PsiMember)) return true;
    if (I_WAS_HERE.get() != null) return true;

    try {
      I_WAS_HERE.set(Boolean.TRUE);
      final TextOccurenceProcessor processor = new MyTextOccurenceProcessor(elementToSearch, consumer);

      final SearchScope groovySearchScope = PsiUtil.restrictScopeToGroovyFiles(new Computable<SearchScope>() {
        public SearchScope compute() {
          return parameters.getEffectiveSearchScope();
        }
      });

      if (!ReferencesSearch.search(elementToSearch, groovySearchScope).forEach(new MyProcessor(null, processor))) return false;

      if (elementToSearch instanceof PsiMethod && isAccessor(elementToSearch)) {
        final PsiField field = ApplicationManager.getApplication().runReadAction(new Computable<PsiField>() {
          public PsiField compute() {
            return GroovyPropertyUtils.findFieldForAccessor((PsiMethod)elementToSearch, true);
          }
        });
        if (field != null) {
          final String prefix = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
            public String compute() {
              return GroovyPropertyUtils.getAccessorPrefix((PsiMethod)elementToSearch);
            }
          });
          if (prefix == null) return true;
          if (!ReferencesSearch.search(field, groovySearchScope).forEach(new MyProcessor(prefix, processor))) return false;
        }
      }
      return true;
    }
    finally {
      I_WAS_HERE.set(null);
    }

  }

  private static Boolean isAccessor(final PsiElement elementToSearch) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return GroovyPropertyUtils.isSimplePropertyAccessor((PsiMethod)elementToSearch);
      }
    });
  }

  static class MyProcessor implements Processor<PsiReference> {
    private final String prefix;
    private final TextOccurenceProcessor processor;

    MyProcessor(@Nullable String prefix, TextOccurenceProcessor processor) {
      this.prefix = prefix;
      this.processor = processor;
    }

    public boolean process(PsiReference psiReference) {
      final PsiElement element = psiReference.getElement();
      if (element == null) return true;

      String alias = getAlias(element);
      if (alias == null) return true;

      final PsiFile containingFile = ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
        public PsiFile compute() {
          return element.getContainingFile();
        }
      });

      if (prefix != null) {
        if (!PsiManager.getInstance(element.getProject()).getSearchHelper()
          .processElementsWithWord(processor, GlobalSearchScope.fileScope(containingFile), prefix + GroovyPropertyUtils.capitalize(alias),
                                   UsageSearchContext.IN_CODE,
                                   false)) {
          return false;
        }
      }

      return PsiManager.getInstance(element.getProject()).getSearchHelper()
        .processElementsWithWord(processor, GlobalSearchScope.fileScope(containingFile), alias, UsageSearchContext.IN_CODE, false);
    }

    private static String getAlias(final PsiElement element) {
      return ApplicationManager.getApplication().runReadAction(new NullableComputable<String>() {
        public String compute() {
          if (!(element.getParent() instanceof GrImportStatement)) return null;
          final GrImportStatement importStatement = (GrImportStatement)element.getParent();
          if (!importStatement.isAliasedImport()) return null;
          return importStatement.getImportedName();
        }
      });
    }

  }

static class MyTextOccurenceProcessor implements TextOccurenceProcessor {
    private final PsiElement elementToSearch;
    private final Processor<PsiReference> consumer;

    public MyTextOccurenceProcessor(PsiElement elementToSearch, Processor<PsiReference> consumer) {
      this.elementToSearch = elementToSearch;
      this.consumer = consumer;
    }

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
  }
}
