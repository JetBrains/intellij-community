package ru.adelf.idea.dotenv.extension;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.ProcessingContext;
import com.intellij.util.indexing.FileBasedIndex;
import com.jetbrains.php.lang.psi.elements.FunctionReference;
import com.jetbrains.php.lang.psi.elements.ParameterList;
import com.jetbrains.php.lang.psi.elements.StringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import ru.adelf.idea.dotenv.indexing.DotenvKeysIndex;

import java.util.Arrays;

public class CompletionContributor extends com.intellij.codeInsight.completion.CompletionContributor {
    public CompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), new CompletionProvider<CompletionParameters>() {
            @Override
            protected void addCompletions(@NotNull CompletionParameters completionParameters, ProcessingContext processingContext, @NotNull CompletionResultSet completionResultSet) {

                PsiElement psiElement = completionParameters.getOriginalPosition();
                if (psiElement == null) {
                    return;
                }

                psiElement = psiElement.getParent();

                if(!(psiElement instanceof StringLiteralExpression)) {
                    return;
                }

                if(!isFunctionReference(psiElement, 0, "getenv", "env")) {
                    return;
                }

                Project project = psiElement.getProject();
                FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();

                fileBasedIndex.processAllKeys(DotenvKeysIndex.KEY, s -> {
                    if(fileBasedIndex.getContainingFiles(DotenvKeysIndex.KEY, s, GlobalSearchScope.allScope(project)).size() > 0) {
                        completionResultSet.addElement(LookupElementBuilder.create(s));
                    }
                    return true;
                }, project);
            }
        });
    }

    private boolean isFunctionReference(PsiElement psiElement, int parameterIndex, String... funcName) {
        PsiElement variableContext = psiElement.getContext();
        if(!(variableContext instanceof ParameterList)) {
            return false;
        } else {
            ParameterList parameterList = (ParameterList)variableContext;
            PsiElement context = parameterList.getContext();
            if(!(context instanceof FunctionReference)) {
                return false;
            } else {
                FunctionReference methodReference = (FunctionReference)context;
                String name = methodReference.getName();

                return (name != null && Arrays.asList(funcName).contains(name) && getParameterIndex(parameterList, psiElement) == parameterIndex);
            }
        }
    }

    private int getParameterIndex(ParameterList parameterList, PsiElement parameter) {
        PsiElement[] parameters = parameterList.getParameters();
        for(int i = 0; i < parameters.length; i = i + 1) {
            if(parameters[i].equals(parameter)) {
                return i;
            }
        }

        return -1;
    }
}
