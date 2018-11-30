package ru.adelf.idea.dotenv.extension;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;
import ru.adelf.idea.dotenv.psi.DotEnvKey;
import ru.adelf.idea.dotenv.psi.DotEnvProperty;
import ru.adelf.idea.dotenv.util.EnvironmentVariablesUtil;

public class DotEnvKeyGotoHandler implements GotoDeclarationHandler {

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {

        if(psiElement == null || psiElement.getParent() == null) {
            return new PsiElement[0];
        }

        psiElement = psiElement.getParent();

        if(!(psiElement instanceof DotEnvKey)) {
            return new PsiElement[0];
        }

        return EnvironmentVariablesApi.getKeyUsages(psiElement.getProject(), psiElement.getText());
    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        return null;
    }
}
