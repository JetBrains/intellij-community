// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightBundle;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtTryExpression;

public class KotlinTryCatchSurrounder extends KotlinTrySurrounderBase<KtElement> {

    @Override
    protected String getCodeTemplate() {
        return "try { \n} catch(e: Exception) {\nTODO(\"Not yet implemented\")\n}";
    }

    @Override
    protected KtElement getSelectionElement(@NotNull KtTryExpression expression) {
        return getCatchTypeParameter(expression);
    }

    @SuppressWarnings("DialogTitleCapitalization")
    @Override
    public String getTemplateDescription() {
        return KotlinCodeInsightBundle.message("surround.with.try.catch.template");
    }
}
