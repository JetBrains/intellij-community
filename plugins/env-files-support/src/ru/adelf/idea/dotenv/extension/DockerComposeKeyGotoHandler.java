package ru.adelf.idea.dotenv.extension;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.YAMLSequenceItem;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;
import ru.adelf.idea.dotenv.psi.DotEnvProperty;
import ru.adelf.idea.dotenv.util.EnvironmentVariablesUtil;

public class DockerComposeKeyGotoHandler implements GotoDeclarationHandler {

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {

        if(psiElement == null || psiElement.getParent() == null || psiElement.getParent().getParent() == null || psiElement.getParent().getParent().getParent() == null) {
            return new PsiElement[0];
        }

        if(!psiElement.getContainingFile().getName().equals("docker-compose.yml") && !psiElement.getContainingFile().getName().equals("docker-compose.yaml")) {
            return new PsiElement[0];
        }

        psiElement = psiElement.getParent();

        if(!(psiElement instanceof YAMLScalar)) {
            return new PsiElement[0];
        }

        if(!(psiElement.getParent() instanceof YAMLSequenceItem)) {
            return new PsiElement[0];
        }

        PsiElement yamlKeyValue = psiElement.getParent().getParent().getParent();

        if(!(yamlKeyValue instanceof YAMLKeyValue)) {
            return new PsiElement[0];
        }

        if(!"environment".equals(((YAMLKeyValue) yamlKeyValue).getKeyText())) {
            return new PsiElement[0];
        }

        return EnvironmentVariablesApi.getKeyUsages(psiElement.getProject(), EnvironmentVariablesUtil.getKeyFromString(((YAMLScalar) psiElement).getTextValue()));
    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        return null;
    }
}
