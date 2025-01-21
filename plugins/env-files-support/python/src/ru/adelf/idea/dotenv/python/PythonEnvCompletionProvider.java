package ru.adelf.idea.dotenv.python;

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
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.DotEnvSettings;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;
import ru.adelf.idea.dotenv.common.BaseEnvCompletionProvider;

public class PythonEnvCompletionProvider extends BaseEnvCompletionProvider implements GotoDeclarationHandler {
    public PythonEnvCompletionProvider() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
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

        PyStringLiteralExpression stringLiteral = getStringLiteral(psiElement);

        if (stringLiteral == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        return EnvironmentVariablesApi.getKeyDeclarations(psiElement.getProject(), stringLiteral.getStringValue());
    }

    @Nullable
    private PyStringLiteralExpression getStringLiteral(@NotNull PsiElement psiElement) {
        PsiElement parent = psiElement.getParent();

        if (!(parent instanceof PyStringLiteralExpression)) {
            return null;
        }

        if (parent.getParent() == null) {
            return null;
        }

        PsiElement candidate = parent.getParent().getParent();

        if (candidate instanceof PyCallExpression) {
            PyCallExpression callExpression = (PyCallExpression) candidate;
            if (PythonPsiHelper.checkGetMethodCall(callExpression)
                && callExpression.getArgumentList() != null
                && callExpression.getArgumentList().getArguments().length > 0
                && callExpression.getArgumentList().getArguments()[0].isEquivalentTo(parent)) {

                return (PyStringLiteralExpression) parent;
            }

            return null;
        }

        if (candidate instanceof PyAssignmentStatement) {
            PyExpression assignedValue = ((PyAssignmentStatement) candidate).getAssignedValue();
            if (assignedValue instanceof PySubscriptionExpression) {
                if (PythonPsiHelper.checkIndexCall((PySubscriptionExpression) assignedValue)) {
                    return (PyStringLiteralExpression) parent;
                }

                return null;
            }
        }

        return null;
    }

    @Nullable
    @Override
    public String getActionText(@NotNull DataContext dataContext) {
        return null;
    }
}
