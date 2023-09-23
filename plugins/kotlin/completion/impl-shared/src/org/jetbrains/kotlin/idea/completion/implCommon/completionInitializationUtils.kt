// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion

import com.intellij.codeInsight.completion.CompletionInitializationContext

fun CompletionInitializationContext.markReplacementOffsetAsModified() {
    // set replacement offset explicitly to mark it as modified
    replacementOffset = replacementOffset
}