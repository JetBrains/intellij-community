// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

@IntellijInternalApi
interface KotlinAutoImportsFilter {
    /**
     * Even if the option to perform auto import is disabled for Kotlin but [forceAutoImportForFile] is true, auto import must happen
     */
    fun forceAutoImportForFile(file: KtFile): Boolean

    /**
     * Allows transforming suggested imports list by any rule.
     */
    fun filterSuggestions(suggestions: Collection<FqName>): Collection<FqName>

    companion object {
        val EP_NAME = ExtensionPointName.create<KotlinAutoImportsFilter>("org.jetbrains.kotlin.idea.codeInsight.unambiguousImports")

        fun findRelevantExtension(file: KtFile): KotlinAutoImportsFilter? = EP_NAME.findFirstSafe { it.forceAutoImportForFile(file) }

        fun filterSuggestionsIfApplicable(context: KtFile, suggestions: Collection<FqName>): Collection<FqName>? =
            findRelevantExtension(context)?.filterSuggestions(suggestions)
    }
}