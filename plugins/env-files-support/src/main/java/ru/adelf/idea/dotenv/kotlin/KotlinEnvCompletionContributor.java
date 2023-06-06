package ru.adelf.idea.dotenv.kotlin;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry;
import ru.adelf.idea.dotenv.DotEnvSettings;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;
import ru.adelf.idea.dotenv.common.BaseEnvCompletionProvider;

public class KotlinEnvCompletionContributor extends BaseEnvCompletionProvider implements GotoDeclarationHandler {
    public KotlinEnvCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().withParent(KtLiteralStringTemplateEntry.class), new CompletionProvider<CompletionParameters>() {
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

        KtLiteralStringTemplateEntry stringLiteral = getStringLiteral(psiElement);

        if (stringLiteral == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        return EnvironmentVariablesApi.getKeyDeclarations(psiElement.getProject(), stringLiteral.getText());
    }

    @Nullable
    private KtLiteralStringTemplateEntry getStringLiteral(@NotNull PsiElement psiElement) {
        PsiElement parent = psiElement.getParent();

        if (!(parent instanceof KtLiteralStringTemplateEntry)) {
            return null;
        }

        if (!KotlinPsiHelper.isEnvStringLiteral((KtLiteralStringTemplateEntry) parent)) {
            return null;
        }

        return (KtLiteralStringTemplateEntry) parent;
    }

    @Nullable
    @Override
    public String getActionText(@NotNull DataContext dataContext) {
        return null;
    }
}
