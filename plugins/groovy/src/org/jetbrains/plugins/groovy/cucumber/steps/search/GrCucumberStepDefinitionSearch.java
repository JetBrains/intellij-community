package org.jetbrains.plugins.groovy.cucumber.steps.search;

import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Max Medvedev
 */
public class GrCucumberStepDefinitionSearch implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final ReferencesSearch.SearchParameters queryParameters, @NotNull final Processor<PsiReference> consumer) {
    //todo
    return true;
    /*final PsiElement element = queryParameters.getElementToSearch();
    if (!(element instanceof GrMethod)) return true;
    if (!GrCucumberUtil.isStepDefinition(element)) return true;

    final GrMethod method = (GrMethod)element;
    final GrAnnotation stepAnnotation = GrCucumberUtil.getCucumberAnnotation(method);
    final String regexp = GrCucumberUtil.getPatternFromStepDefinition(stepAnnotation);

    final String word = CucumberUtil.getTheBiggestWordToSearchByIndex(regexp);
    if (StringUtil.isEmpty(word)) return true;

    final SearchScope searchScope = CucumberStepSearchUtil.restrictScopeToGherkinFiles(new Computable<SearchScope>() {
      public SearchScope compute() {
        return queryParameters.getEffectiveSearchScope();
      }
    });
    // As far as default CacheBasedRefSearcher doesn't look for references in string we have to write out own to handle this correctly
    final TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      @Override
      public boolean execute(PsiElement element, int offsetInElement) {
        for (PsiReference ref : element.getReferences()) {
          if (ref != null && ref.isReferenceTo(element)) {
            if (!consumer.process(ref)){
              return false;
            }
          }
        }
        return true;
      }
    };

    short context = UsageSearchContext.IN_STRINGS | UsageSearchContext.IN_CODE;
    PsiSearchHelper instance = PsiSearchHelper.SERVICE.getInstance(element.getProject());
    return instance.processElementsWithWord(processor, searchScope, word, context, true);*/
  }
}
