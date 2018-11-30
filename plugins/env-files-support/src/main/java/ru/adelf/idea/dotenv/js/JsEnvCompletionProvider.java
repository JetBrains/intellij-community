package ru.adelf.idea.dotenv.js;

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
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;
import ru.adelf.idea.dotenv.common.BaseEnvCompletionProvider;

public class JsEnvCompletionProvider extends BaseEnvCompletionProvider implements GotoDeclarationHandler {
    public JsEnvCompletionProvider() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {

                PsiElement psiElement = completionParameters.getOriginalPosition();
                if(psiElement == null || !JsPsiHelper.checkPsiElement(psiElement)) {
                    return;
                }

                fillCompletionResultSet(completionResultSet, psiElement.getProject());
            }
        });
    }

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {

        if(psiElement == null) {
            return new PsiElement[0];
        }

        if(!JsPsiHelper.checkPsiElement(psiElement)) {
            return new PsiElement[0];
        }

        return EnvironmentVariablesApi.getKeyDeclarations(psiElement.getProject(), psiElement.getText());
    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        return null;
    }
}