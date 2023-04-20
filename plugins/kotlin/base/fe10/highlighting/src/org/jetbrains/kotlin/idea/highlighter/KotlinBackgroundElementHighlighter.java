// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighter;

import com.intellij.codeInsight.highlighting.BackgroundElementHighlighter;

public class KotlinBackgroundElementHighlighter implements BackgroundElementHighlighter {
    @Override
    public boolean isImmediatelyHighlightAllowed() {
        return false;
    }
}
