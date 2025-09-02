// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement;

import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.util.ElementKind;
import org.jetbrains.kotlin.psi.KtExpression;

import static org.jetbrains.kotlin.idea.base.psi.KotlinPsiUtils.getElementAtOffsetIgnoreWhitespaceAfter;
import static org.jetbrains.kotlin.idea.base.psi.KotlinPsiUtils.getElementAtOffsetIgnoreWhitespaceBefore;
import static org.jetbrains.kotlin.idea.util.FindElementUtils.findElements;

public class KotlinStatementSurroundDescriptor implements SurroundDescriptor {

    private static final Surrounder[] SURROUNDERS = {
            new KotlinIfSurrounder(),
            new KotlinIfElseSurrounder(),
            new KotlinFunctionLiteralSurrounder(),
            new KotlinTryFinallySurrounder(),
            new KotlinTryCatchFinallySurrounder(),
            new KotlinTryCatchSurrounder()
    };

    @Override
    public PsiElement @NotNull[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
        PsiElement[] elements = findElements(file, startOffset, endOffset, ElementKind.EXPRESSION);
        if (elements.length > 0) return elements;

        var element1 = getElementAtOffsetIgnoreWhitespaceBefore(file, startOffset);
        var element2 = getElementAtOffsetIgnoreWhitespaceAfter(file, endOffset);
        if (element1 == null || element2 == null) return PsiElement.EMPTY_ARRAY;
        if (element1.getTextRange().getStartOffset() > element2.getTextRange().getEndOffset()) return PsiElement.EMPTY_ARRAY;

        PsiElement commonParent = PsiTreeUtil.findCommonParent(element1, element2);
        return commonParent instanceof KtExpression ? new PsiElement[] {commonParent} : PsiElement.EMPTY_ARRAY;
    }

    @Override
    public Surrounder @NotNull[] getSurrounders() {
        return SURROUNDERS;
    }

    @Override
    public boolean isExclusive() {
        return false;
    }
}
