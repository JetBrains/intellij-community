package org.jetbrains.plugins.groovy.cucumber.steps.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Max Medvedev
 */
public class GrCucumberMethodUsageSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull MethodReferencesSearch.SearchParameters p, @NotNull final Processor<PsiReference> consumer) {
    //todo
    /*if (!(p.getMethod() instanceof GrMethod)) return;
    final GrMethod method = (GrMethod)p.getMethod();
    final GrAnnotation stepAnnotation = GrCucumberUtil.getCucumberAnnotation(method);
    if (stepAnnotation == null) return;

    final String regexp = GrCucumberUtil.getPatternFromStepDefinition(stepAnnotation);
    final String word = CucumberUtil.getTheBiggestWordToSearchByIndex(regexp);
    if (StringUtil.isEmpty(word)) return;

    if (p.getScope() instanceof GlobalSearchScope) {
      GlobalSearchScope restrictedScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)p.getScope(),
                                                                                          GherkinFileType.INSTANCE);
      ReferencesSearch.search(new ReferencesSearch.SearchParameters(method, restrictedScope, false, p.getOptimizer())).forEach(consumer);
    }*/
  }
}