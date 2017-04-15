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
import ru.adelf.idea.dotenv.indexing.DotEnvUsagesIndex;
import ru.adelf.idea.dotenv.psi.DotEnvProperty;
import ru.adelf.idea.dotenv.util.DotEnvCallsVisitor;

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

        String key = psiElement.getText().split("=")[0].trim();
        final DotEnvCallsVisitor visitor = new DotEnvCallsVisitor();
        Project project = psiElement.getProject();

        FileBasedIndex.getInstance().getFilesWithKey(DotEnvUsagesIndex.KEY, new HashSet<>(Collections.singletonList(key)), virtualFile -> {
            PsiFile psiFileTarget = PsiManager.getInstance(project).findFile(virtualFile);
            if(psiFileTarget == null) {
                return true;
            }

            psiFileTarget.acceptChildren(visitor);

            return true;
        }, GlobalSearchScope.allScope(project));

        return visitor.getTargets(key);
    }

    @Nullable
    @Override
    public String getActionText(DataContext dataContext) {
        return null;
    }
}
