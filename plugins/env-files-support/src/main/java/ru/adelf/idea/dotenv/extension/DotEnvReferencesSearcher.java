package ru.adelf.idea.dotenv.extension;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.psi.DotEnvProperty;

public class DotEnvReferencesSearcher  extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
    public DotEnvReferencesSearcher() {
        super(true);
    }

    @Override
    public void processQuery(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull Processor<? super PsiReference> consumer) {
        PsiElement refElement = queryParameters.getElementToSearch();
        if (!(refElement instanceof DotEnvProperty)) return;

        addPropertyUsages((DotEnvProperty)refElement, queryParameters.getEffectiveSearchScope(), queryParameters.getOptimizer());
    }

    private static void addPropertyUsages(@NotNull DotEnvProperty property, @NotNull SearchScope scope, @NotNull SearchRequestCollector collector) {
        final String propertyName = property.getName();
        if (StringUtil.isNotEmpty(propertyName)) {
            /*SearchScope additional = GlobalSearchScope.EMPTY_SCOPE;
            for (CustomPropertyScopeProvider provider : CustomPropertyScopeProvider.EP_NAME.getExtensionList()) {
                additional = additional.union(provider.getScope(property.getProject()));
            }

            SearchScope propScope = scope.intersectWith(property.getUseScope()).intersectWith(additional);*/
            collector.searchWord(propertyName, scope, UsageSearchContext.ANY, true, property);
            collector.searchWord("process.env." + propertyName, scope, UsageSearchContext.ANY, true, property);
        }
    }
}
