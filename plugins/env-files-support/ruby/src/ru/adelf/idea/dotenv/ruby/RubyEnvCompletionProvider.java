package ru.adelf.idea.dotenv.ruby;

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
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RStringLiteral;
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RArrayIndexing;
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RConstant;
import ru.adelf.idea.dotenv.DotEnvSettings;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;
import ru.adelf.idea.dotenv.common.BaseEnvCompletionProvider;

import java.util.Objects;

public class RubyEnvCompletionProvider extends BaseEnvCompletionProvider implements GotoDeclarationHandler {
    public RubyEnvCompletionProvider() {
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

        RStringLiteral stringLiteral = getStringLiteral(psiElement);

        if (stringLiteral == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        return EnvironmentVariablesApi.getKeyDeclarations(psiElement.getProject(), stringLiteral.getContent());
    }

    @Nullable
    private RStringLiteral getStringLiteral(@NotNull PsiElement psiElement) {
        PsiElement parent = psiElement.getParent();

        if (!(parent instanceof RStringLiteral)) {
            return null;
        }

        if (parent.getParent() == null) {
            return null;
        }

        PsiElement array = parent.getParent().getParent();

        if (!(array instanceof RArrayIndexing)) {
            return null;
        }

        PsiElement receiver = ((RArrayIndexing) array).getReceiver();

        if (!(receiver instanceof RConstant)) {
            return null;
        }

        if (receiver.getFirstChild() == null) {
            return null;
        }

        if (!Objects.equals(receiver.getFirstChild().getText(), "ENV")) {
            return null;
        }

        return (RStringLiteral) parent;
    }

    @Nullable
    @Override
    public String getActionText(@NotNull DataContext dataContext) {
        return null;
    }
}
