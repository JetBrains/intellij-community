package ru.adelf.idea.dotenv.docker;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.docker.dockerFile.parser.psi.DockerFileEnvRegularDeclaration;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;
import ru.adelf.idea.dotenv.util.EnvironmentVariablesUtil;

public class DockerfileKeyGotoHandler implements GotoDeclarationHandler {

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {
        if (psiElement == null || psiElement.getParent() == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        if (!psiElement.getContainingFile().getName().equals("Dockerfile")) {
            return PsiElement.EMPTY_ARRAY;
        }

        psiElement = psiElement.getParent();

        if (!(psiElement instanceof DockerFileEnvRegularDeclaration)) {
            return PsiElement.EMPTY_ARRAY;
        }

        return EnvironmentVariablesApi.getKeyUsages(psiElement.getProject(), EnvironmentVariablesUtil.getKeyFromString((((DockerFileEnvRegularDeclaration) psiElement).getDeclaredName().getText())));
    }

    @Nullable
    @Override
    public String getActionText(@NotNull DataContext dataContext) {
        return null;
    }
}
