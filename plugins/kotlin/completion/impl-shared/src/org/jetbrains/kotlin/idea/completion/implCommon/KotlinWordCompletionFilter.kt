// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.implCommon

import com.intellij.lang.DefaultWordCompletionFilter

class KotlinWordCompletionFilter : DefaultWordCompletionFilter() {
    override fun isWordCompletionInDumbModeEnabled(): Boolean {
        // When using PSI completion in dumb mode, do not show the word completion
        return !isDumbPsiCompletionEnabled()
    }
}