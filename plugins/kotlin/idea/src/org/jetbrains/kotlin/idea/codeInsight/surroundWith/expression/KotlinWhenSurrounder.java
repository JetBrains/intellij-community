// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.descriptors.ClassDescriptor;
import org.jetbrains.kotlin.descriptors.ClassKind;
import org.jetbrains.kotlin.descriptors.ClassifierDescriptor;
import org.jetbrains.kotlin.idea.caches.resolve.ResolutionUtils;
import org.jetbrains.kotlin.idea.core.surroundWith.KotlinExpressionSurrounder;
import org.jetbrains.kotlin.psi.*;
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode;
import org.jetbrains.kotlin.types.KotlinType;

public class KotlinWhenSurrounder extends KotlinExpressionSurrounder {
    @Override
    public String getTemplateDescription() {
        return "when (expr) {}";
    }

    @Nullable
    @Override
    public TextRange surroundExpression(@NotNull Project project, @NotNull Editor editor, @NotNull KtExpression expression) {
        KtWhenExpression whenExpression = (KtWhenExpression) KtPsiFactoryKt
                .KtPsiFactory(expression).createExpression(getCodeTemplate(expression));
        KtExpression subjectExpression = whenExpression.getSubjectExpression();
        assert subjectExpression != null : "JetExpression should exists for " + whenExpression.getText() + " expression";
        subjectExpression.replace(expression);

        expression = (KtExpression) expression.replace(whenExpression);

        CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(expression);

        KtWhenEntry whenEntry = ((KtWhenExpression) expression).getEntries().get(0);
        KtWhenCondition whenEntryCondition = whenEntry.getConditions()[0];
        assert whenEntryCondition != null : "JetExpression for first entry should exists: " + expression.getText();
        TextRange whenRange = whenEntryCondition.getTextRange();
        editor.getDocument().deleteString(whenRange.getStartOffset(), whenRange.getEndOffset());
        int offset = whenRange.getStartOffset();
        return new TextRange(offset, offset);
    }

    private String getCodeTemplate(KtExpression expression) {
        KotlinType type = ResolutionUtils.analyze(expression, BodyResolveMode.PARTIAL).getType(expression);
        if (type != null) {
            ClassifierDescriptor descriptor = type.getConstructor().getDeclarationDescriptor();
            if (descriptor instanceof ClassDescriptor && ((ClassDescriptor) descriptor).getKind() == ClassKind.ENUM_CLASS) {
                return "when(a) { \nb -> {}\n}";
            }
        }
        return "when(a) { \nb -> {}\n else -> {}\n}";
    }


}
