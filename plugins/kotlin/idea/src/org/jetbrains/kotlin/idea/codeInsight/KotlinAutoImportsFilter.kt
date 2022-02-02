// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

@IntellijInternalApi
interface KotlinAutoImportsFilter {
    /**
     * Even if the option to perform auto import is disabled for Kotlin but [forceAutoImportForElement] is true, auto import must happen
     */
    fun forceAutoImportForElement(file: KtFile, suggestions: Collection<FqName>): Boolean

    /**
     * Allows transforming suggested imports list by any rule.
     */
    fun filterSuggestions(suggestions: Collection<FqName>): Collection<FqName>

    companion object {
        val EP_NAME = ExtensionPointName.create<KotlinAutoImportsFilter>("org.jetbrains.kotlin.idea.codeInsight.unambiguousImports")

        private fun findRelevantExtension(file: KtFile, suggestions: Collection<FqName>): KotlinAutoImportsFilter? =
            EP_NAME.findFirstSafe { it.forceAutoImportForElement(file, suggestions) }

        fun filterSuggestionsIfApplicable(context: KtFile, suggestions: Collection<FqName>): Collection<FqName> {
            val extension = findRelevantExtension(context, suggestions)

            if (extension != null) return extension.filterSuggestions(suggestions)
            
            return if (KotlinCodeInsightSettings.getInstance().addUnambiguousImportsOnTheFly) {
                suggestions
            } else {
                emptyList()
            }
        }
    }
}