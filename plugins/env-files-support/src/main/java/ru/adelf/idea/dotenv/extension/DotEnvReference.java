package ru.adelf.idea.dotenv.extension;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;

import java.util.Arrays;

public class DotEnvReference extends PsiReferenceBase<PsiElement> implements PsiPolyVariantReference {
    private final String key;

    public DotEnvReference(@NotNull PsiElement element, TextRange textRange) {
        super(element, textRange);
        key = element.getText().substring(textRange.getStartOffset(), textRange.getEndOffset());
    }

    public DotEnvReference(@NotNull PsiElement element, TextRange textRange, String key) {
        super(element, textRange);
        this.key = key;
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        final PsiElement[] elements = EnvironmentVariablesApi.getKeyDeclarations(myElement.getProject(), key);

        return Arrays.stream(elements)
                .filter(psiElement -> psiElement instanceof PsiNamedElement)
                .map(PsiElementResolveResult::new)
                .toArray(ResolveResult[]::new);
    }

    @Nullable
    @Override
    public PsiElement resolve() {
        ResolveResult[] resolveResults = multiResolve(false);
        return resolveResults.length == 1 ? resolveResults[0].getElement() : null;
    }

    @NotNull
    @Override
    public Object[] getVariants() {
        Project project = myElement.getProject();
        final PsiElement[] elements = EnvironmentVariablesApi.getKeyDeclarations(project, key);

        return Arrays.stream(elements)
                .filter(psiElement -> psiElement instanceof PsiNamedElement)
                .map(psiElement -> LookupElementBuilder.create(psiElement).
                        withTypeText(psiElement.getContainingFile().getName()))
                .toArray(LookupElement[]::new);
    }
}