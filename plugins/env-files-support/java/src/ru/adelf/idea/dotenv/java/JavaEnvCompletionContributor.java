package ru.adelf.idea.dotenv.java;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.DotEnvSettings;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;
import ru.adelf.idea.dotenv.common.BaseEnvCompletionProvider;

public class JavaEnvCompletionContributor extends BaseEnvCompletionProvider implements GotoDeclarationHandler {
    public JavaEnvCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(PsiLiteralExpression.class), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters completionParameters, @NotNull ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {
                PsiElement psiElement = completionParameters.getOriginalPosition();

                if (psiElement == null || !DotEnvSettings.getInstance().completionEnabled) {
                    return;
                }

                if (getStringLiteral(psiElement) == null) {
                    return;
                }

                fillCompletionResultSet(completionResultSet, psiElement.getProject());
            }
        });
    }

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        PsiLiteralExpression stringLiteral = getStringLiteral(psiElement);

        if (stringLiteral == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        Object value = stringLiteral.getValue();

        if (!(value instanceof String)) {
            return PsiElement.EMPTY_ARRAY;
        }

        return EnvironmentVariablesApi.getKeyDeclarations(psiElement.getProject(), (String) value);
    }

    @Nullable
    private PsiLiteralExpression getStringLiteral(@NotNull PsiElement psiElement) {
        PsiElement parent = psiElement.getParent();

        if (!(parent instanceof PsiLiteralExpression)) {
            return null;
        }

        if (!JavaPsiHelper.isEnvStringLiteral((PsiLiteralExpression) parent)) {
            return null;
        }

        return (PsiLiteralExpression) parent;
    }

    @Nullable
    @Override
    public String getActionText(@NotNull DataContext dataContext) {
        return null;
    }
}
