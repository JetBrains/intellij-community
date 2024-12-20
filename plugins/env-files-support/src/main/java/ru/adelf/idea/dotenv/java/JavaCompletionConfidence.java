package ru.adelf.idea.dotenv.java;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

public class JavaCompletionConfidence extends CompletionConfidence {
    @NotNull
    @Override
    public ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
        PsiElement literal = contextElement.getContext();
        if(!(literal instanceof PsiLiteralExpression)) {
            return ThreeState.UNSURE;
        }

        if(JavaPsiHelper.isEnvStringLiteral((PsiLiteralExpression) literal)) {
            return ThreeState.NO;
        }

        return ThreeState.UNSURE;
    }
}