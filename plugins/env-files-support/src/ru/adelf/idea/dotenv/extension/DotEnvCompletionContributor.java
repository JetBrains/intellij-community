package ru.adelf.idea.dotenv.extension;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;
import ru.adelf.idea.dotenv.util.PsiUtil;

import java.util.Map;

public class DotEnvCompletionContributor extends CompletionContributor implements GotoDeclarationHandler {
    public DotEnvCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {

                PsiElement psiElement = completionParameters.getOriginalPosition();
                if(psiElement == null || getStringLiteral(psiElement) == null) {
                    return;
                }

                for(Map.Entry<String, String> entry : EnvironmentVariablesApi.getAllKeyValues(psiElement.getProject()).entrySet()) {
                    LookupElementBuilder lockup = LookupElementBuilder.create(entry.getKey());

                    if(StringUtils.isNotEmpty(entry.getValue())) {
                        completionResultSet.addElement(lockup.withTailText(" = " + entry.getValue(), true));
                    } else {
                        completionResultSet.addElement(lockup);
                    }
                }
            }
        });
    }

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {

        if(psiElement == null) {
            return new PsiElement[0];
        }

        StringLiteralExpression stringLiteral = getStringLiteral(psiElement);

        if(stringLiteral == null) {
            return new PsiElement[0];
        }

        return EnvironmentVariablesApi.getKeyDeclarations(psiElement.getProject(), stringLiteral.getContents());
    }

    @Nullable
    private StringLiteralExpression getStringLiteral(@NotNull PsiElement psiElement) {
        PsiElement parent = psiElement.getParent();

        if(!(parent instanceof StringLiteralExpression)) {
            return null;
        }

        if(!PsiUtil.isEnvFunctionParameter(parent)) {
            return null;
        }

        return (StringLiteralExpression) parent;
    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        return null;
    }
}
