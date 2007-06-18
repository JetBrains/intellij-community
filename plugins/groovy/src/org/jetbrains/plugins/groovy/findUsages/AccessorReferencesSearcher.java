package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * author ven
 */
public class AccessorReferencesSearcher implements QueryExecutor<PsiReference, MethodReferencesSearch.SearchParameters> {

  public boolean execute(final MethodReferencesSearch.SearchParameters searchParameters, final Processor<PsiReference> consumer) {
    final PsiMethod method = searchParameters.getMethod();
    if (PropertyUtil.isSimplePropertyAccessor(method)) {
      final String propertyName = PropertyUtil.getPropertyName(method);
      assert propertyName != null;
      SearchScope searchScope = ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
        public SearchScope compute() {
          SearchScope searchScope = searchParameters.getScope();
          if (searchScope instanceof GlobalSearchScope) {
            searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope) searchScope, GroovyFileType.GROOVY_FILE_TYPE);
          }
          return searchScope;
        }
      });

      final PsiSearchHelper helper = PsiManager.getInstance(method.getProject()).getSearchHelper();
      final TextOccurenceProcessor processor = new TextOccurenceProcessor() {
        public boolean execute(PsiElement element, int offsetInElement) {
          final PsiReference[] refs = element.getReferences();
          for (PsiReference ref : refs) {
            if (ref.getRangeInElement().contains(offsetInElement)) {
              if (ref.isReferenceTo(method)) {
                return consumer.process(ref);
              }
            }
          }
          return true;
        }
      };

      if (!helper.processElementsWithWord(processor,
          searchScope,
          propertyName,
          UsageSearchContext.IN_CODE,
          false)) {
        return false;
      }
    }

    return true;
  }
}
