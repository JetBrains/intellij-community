// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightBundle;
import org.jetbrains.kotlin.name.ClassId;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtTryExpression;

import java.util.List;

public class KotlinTryCatchFinallySurrounder extends KotlinTrySurrounderBase<KtElement> {

    @Override
    protected String getCodeTemplate(List<ClassId> exceptionClasses) {
        StringBuilder template = new StringBuilder();
        template.append("try {\n}\n");
        for (ClassId classId: exceptionClasses) {
            template.append("catch(e: ")
                    .append(classId.asFqNameString())
                    .append(") {\nthrow e\n}");
        }
        template.append(" finally {\n}");
        return template.toString();
    }

    @Override
    protected KtElement getSelectionElement(@NotNull KtTryExpression expression) {
        return getCatchTypeParameter(expression);
    }

    @SuppressWarnings("DialogTitleCapitalization")
    @Override
    public String getTemplateDescription() {
        return KotlinCodeInsightBundle.message("surround.with.try.catch.finally.template");
    }
}
