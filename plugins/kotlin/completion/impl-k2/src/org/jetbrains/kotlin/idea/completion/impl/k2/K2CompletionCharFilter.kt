// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.util.registry.RegistryManager
import org.jetbrains.kotlin.idea.completion.KotlinCompletionCharFilter

internal class K2CompletionCharFilter : KotlinCompletionCharFilter() {
    override fun customResult(
        c: Char,
        prefixLength: Int,
        lookup: Lookup
    ): Result? {
        if (!RegistryManager.getInstance().`is`("kotlin.k2.dot.add.enabled")) return null

        if (prefixLength == 0 && !lookup.isSelectionTouched && !lookup.isFocused) {
            val caret = lookup.editor.caretModel.offset
            if (caret > 0 && lookup.editor.document.charsSequence[caret - 1] == '.') {
                return Result.ADD_TO_PREFIX
            }
        }
        return super.customResult(c, prefixLength, lookup)
    }
}