package ru.adelf.idea.dotenv.kotlin;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry;

public class KotlinCompletionConfidence extends CompletionConfidence {
    @NotNull
    @Override
    public ThreeState shouldSkipAutopopup(@NotNull PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
        PsiElement literal = contextElement.getContext();
        if (!(literal instanceof KtLiteralStringTemplateEntry)) {
            return ThreeState.UNSURE;
        }

        if (KotlinPsiHelper.isEnvStringLiteral((KtLiteralStringTemplateEntry) literal)) {
            return ThreeState.NO;
        }

        return ThreeState.UNSURE;
    }
}