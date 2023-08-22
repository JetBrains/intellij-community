// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeInsight.surroundWith.statement;


import com.intellij.codeInsight.CodeInsightBundle;
import org.jetbrains.annotations.NotNull;

public class KotlinIfSurrounder extends KotlinIfSurrounderBase {

    @Override
    public String getTemplateDescription() {
        return CodeInsightBundle.message("surround.with.if.template");
    }

    @NotNull
    @Override
    protected String getCodeTemplate() {
        return "if (a) { \n}";
    }

    @Override
    protected boolean isGenerateDefaultInitializers() {
        return true;
    }
}
