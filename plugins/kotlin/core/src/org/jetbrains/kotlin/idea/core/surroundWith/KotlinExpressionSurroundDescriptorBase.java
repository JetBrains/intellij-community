// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.core.surroundWith;

import com.intellij.lang.surroundWith.SurroundDescriptor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.util.ElementKind;
import org.jetbrains.kotlin.psi.KtExpression;
import static org.jetbrains.kotlin.idea.util.FindElementUtils.findElement;

public abstract class KotlinExpressionSurroundDescriptorBase implements SurroundDescriptor {
    @Override
    public PsiElement @NotNull [] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
        KtExpression expression = (KtExpression) findElement(file, startOffset, endOffset, ElementKind.EXPRESSION);

        return expression == null ? PsiElement.EMPTY_ARRAY : new PsiElement[] {expression};
    }

    @Override
    public boolean isExclusive() {
        return false;
    }
}
