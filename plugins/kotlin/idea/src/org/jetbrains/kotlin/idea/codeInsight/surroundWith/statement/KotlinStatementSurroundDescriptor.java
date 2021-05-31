// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement;

import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.core.util.CodeInsightUtils;

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
    @NotNull
    public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
        return CodeInsightUtils.findElements(file, startOffset, endOffset, CodeInsightUtils.ElementKind.EXPRESSION);
    }

    @Override
    @NotNull public Surrounder[] getSurrounders() {
        return SURROUNDERS;
    }

    @Override
    public boolean isExclusive() {
        return false;
    }
}
