package ru.adelf.idea.dotenv.extension.symbols;

import com.intellij.model.Symbol;
import com.intellij.model.psi.PsiExternalReferenceHost;
import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.model.psi.PsiSymbolReferenceHints;
import com.intellij.model.psi.PsiSymbolReferenceProvider;
import com.intellij.model.search.SearchRequest;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.psi.DotEnvKey;

import java.util.Collection;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class DotEnvSymbolReferenceProvider implements PsiSymbolReferenceProvider {
    @Override
    public @NotNull Collection<? extends PsiSymbolReference> getReferences(@NotNull PsiExternalReferenceHost keyElement,
                                                                           @NotNull PsiSymbolReferenceHints psiSymbolReferenceHints) {
        if (!(keyElement instanceof DotEnvKey)) return List.of();

        return List.of(new DotEnvKeyUsageReference((DotEnvKey) keyElement));
    }

    @Override
    public @NotNull Collection<? extends SearchRequest> getSearchRequests(@NotNull Project project, @NotNull Symbol symbol) {
        return List.of();
    }
}
