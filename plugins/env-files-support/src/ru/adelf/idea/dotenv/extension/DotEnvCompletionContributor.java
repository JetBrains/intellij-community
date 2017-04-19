package ru.adelf.idea.dotenv.extension;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ProcessingContext;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.adelf.idea.dotenv.DotEnvFileType;
import ru.adelf.idea.dotenv.indexing.DotEnvKeyValuesIndex;
import ru.adelf.idea.dotenv.indexing.DotEnvKeysIndex;
import ru.adelf.idea.dotenv.util.DotEnvPsiElementsVisitor;
import ru.adelf.idea.dotenv.util.PsiUtil;

import java.util.Collections;
import java.util.HashSet;

public class DotEnvCompletionContributor extends CompletionContributor implements GotoDeclarationHandler {
    public DotEnvCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {

                PsiElement psiElement = completionParameters.getOriginalPosition();
                if(psiElement == null) {
                    return;
                }

                if(getStringLiteral(psiElement) == null) return;

                Project project = psiElement.getProject();
                FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();

                fileBasedIndex.processAllKeys(DotEnvKeyValuesIndex.KEY, s -> {
                    if(fileBasedIndex.getContainingFiles(DotEnvKeyValuesIndex.KEY, s, GlobalSearchScope.allScope(project)).size() > 0) {

                        String[] splitParts = s.split("=");

                        completionResultSet.addElement(
                                LookupElementBuilder.create(splitParts[0].trim())
                                        .withTailText(" = " + s.substring(splitParts[0].length() + 1).trim(), true));
                    }
                    return true;
                }, project);
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

        final Project project = psiElement.getProject();
        final DotEnvPsiElementsVisitor visitor = new DotEnvPsiElementsVisitor();
        String key = stringLiteral.getContents();

        FileBasedIndex.getInstance().getFilesWithKey(DotEnvKeysIndex.KEY, new HashSet<>(Collections.singletonList(key)), virtualFile -> {
            PsiFile psiFileTarget = PsiManager.getInstance(project).findFile(virtualFile);
            if(psiFileTarget == null) {
                return true;
            }

            psiFileTarget.acceptChildren(visitor);

            return true;
        }, GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.allScope(project), DotEnvFileType.INSTANCE));

        return visitor.getElementsByKey(key);
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
