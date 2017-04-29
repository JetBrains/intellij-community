package ru.adelf.idea.dotenv.extension;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.api.EnvironmentVariablesApi;
import ru.adelf.idea.dotenv.indexing.DotEnvUsagesIndex;
import ru.adelf.idea.dotenv.psi.DotEnvProperty;
import ru.adelf.idea.dotenv.php.PhpEnvironmentCallsVisitor;
import ru.adelf.idea.dotenv.util.EnvironmentVariablesUtil;

import java.util.Collections;
import java.util.HashSet;

public class DotEnvKeyGotoHandler implements GotoDeclarationHandler {

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(@Nullable PsiElement psiElement, int i, Editor editor) {

        if(psiElement == null || psiElement.getParent() == null) {
            return new PsiElement[0];
        }

        psiElement = psiElement.getParent();

        if(!(psiElement instanceof DotEnvProperty)) {
            return new PsiElement[0];
        }

        return EnvironmentVariablesApi.getKeyUsages(psiElement.getProject(), EnvironmentVariablesUtil.getKeyFromString(psiElement.getText()));
    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        return null;
    }
}
