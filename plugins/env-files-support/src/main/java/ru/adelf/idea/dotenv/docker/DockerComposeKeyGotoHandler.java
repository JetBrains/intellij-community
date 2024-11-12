package ru.adelf.idea.dotenv.docker;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLMapping;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;
import ru.adelf.idea.dotenv.util.EnvironmentVariablesUtil;

public class DockerComposeKeyGotoHandler implements GotoDeclarationHandler {
    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {
        if (psiElement == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        if (!psiElement.getContainingFile().getName().equals("docker-compose.yml") && !psiElement.getContainingFile().getName().equals("docker-compose.yaml")) {
            return PsiElement.EMPTY_ARRAY;
        }

        if (psiElement.getParent() == null || psiElement.getParent().getParent() == null || psiElement.getParent().getParent().getParent() == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        psiElement = psiElement.getParent();

        if (psiElement instanceof YAMLScalar) {
            if (!(psiElement.getParent() instanceof YAMLSequenceItem)) {
                return PsiElement.EMPTY_ARRAY;
            }

            PsiElement yamlKeyValue = psiElement.getParent().getParent().getParent();

            if (!(yamlKeyValue instanceof YAMLKeyValue)) {
                return PsiElement.EMPTY_ARRAY;
            }

            if (!"environment".equals(((YAMLKeyValue) yamlKeyValue).getKeyText())) {
                return PsiElement.EMPTY_ARRAY;
            }

            return EnvironmentVariablesApi.getKeyUsages(psiElement.getProject(), EnvironmentVariablesUtil.getKeyFromString(((YAMLScalar) psiElement).getTextValue()));
        }

        if (psiElement instanceof YAMLKeyValue) {
            if (!(psiElement.getParent() instanceof YAMLMapping)) {
                return PsiElement.EMPTY_ARRAY;
            }

            PsiElement yamlKeyValue = psiElement.getParent().getParent();

            if (!(yamlKeyValue instanceof YAMLKeyValue)) {
                return PsiElement.EMPTY_ARRAY;
            }

            if (!"environment".equals(((YAMLKeyValue) yamlKeyValue).getKeyText())) {
                return PsiElement.EMPTY_ARRAY;
            }

            return EnvironmentVariablesApi.getKeyUsages(psiElement.getProject(), ((YAMLKeyValue) psiElement).getKeyText());
        }

        return PsiElement.EMPTY_ARRAY;
    }

    @Nullable
    @Override
    public String getActionText(@NotNull DataContext dataContext) {
        return null;
    }
}
