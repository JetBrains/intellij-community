// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.expression;

import com.intellij.lang.surroundWith.Surrounder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.core.surroundWith.KotlinExpressionSurroundDescriptorBase;

public class KotlinExpressionSurroundDescriptor extends KotlinExpressionSurroundDescriptorBase {
    private static final Surrounder[] SURROUNDERS = {
            new KotlinNotSurrounder(),
            new KotlinStringTemplateSurrounder(),
            new KotlinParenthesesSurrounder(),
            new KotlinWhenSurrounder(),
            new KotlinWithIfExpressionSurrounder(/* withElse = */false),
            new KotlinWithIfExpressionSurrounder(/* withElse = */true),
            new KotlinTryExpressionSurrounder.TryCatch(),
            new KotlinTryExpressionSurrounder.TryCatchFinally(),
            new KotlinIfElseExpressionSurrounder(/* withBraces = */false),
            new KotlinIfElseExpressionSurrounder(/* withBraces = */true)
    };

    @Override
    @NotNull
    public Surrounder[] getSurrounders() {
        return SURROUNDERS;
    }
}
