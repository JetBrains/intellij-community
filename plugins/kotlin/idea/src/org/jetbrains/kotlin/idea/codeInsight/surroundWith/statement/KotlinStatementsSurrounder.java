// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.idea.core.surroundWith.KotlinSurrounderUtils;
import org.jetbrains.kotlin.psi.KtExpression;

public abstract class KotlinStatementsSurrounder implements Surrounder {

    @Override
    public boolean isApplicable(@NotNull PsiElement[] elements) {
        if (elements.length == 0) {
            return false;
        }

        if (elements.length == 1 && elements[0] instanceof KtExpression) {
            if (!isApplicableWhenUsedAsExpression() && KotlinSurrounderUtils.isUsedAsExpression((KtExpression) elements[0])) {
                return false;
            }
        }

        return true;
    }

    protected boolean isApplicableWhenUsedAsExpression() {
        return true;
    }

    @Override
    @Nullable
    public TextRange surroundElements(
            @NotNull Project project,
            @NotNull Editor editor,
            @NotNull PsiElement[] elements) throws IncorrectOperationException {
        PsiElement container = elements[0].getParent();
        if (container == null) return null;
        return surroundStatements(project, editor, container, elements);
    }

    @Nullable
    protected abstract TextRange surroundStatements(
            Project project,
            Editor editor,
            PsiElement container,
            PsiElement[] statements
    );
}
