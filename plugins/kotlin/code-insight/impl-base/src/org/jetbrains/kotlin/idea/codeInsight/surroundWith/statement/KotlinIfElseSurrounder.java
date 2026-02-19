// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement;


import com.intellij.codeInsight.CodeInsightBundle;
import org.jetbrains.annotations.NotNull;

public class KotlinIfElseSurrounder extends KotlinIfSurrounderBase {

    @SuppressWarnings("DialogTitleCapitalization")
    @Override
    public String getTemplateDescription() {
        return CodeInsightBundle.message("surround.with.ifelse.template");
    }

    @Override
    protected @NotNull String getCodeTemplate() {
        return "if (a) { \n} else { \n}";
    }

    @Override
    protected boolean isGenerateDefaultInitializers() {
        return false;
    }
}
