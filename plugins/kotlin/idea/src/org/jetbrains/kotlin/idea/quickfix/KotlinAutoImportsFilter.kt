// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

interface KotlinAutoImportsFilter {
    /**
     * Even if the option to perform auto import is disabled for Kotlin but [forceAutoImportForFile] is true, auto import must happen
     */
    fun forceAutoImportForFile(file: KtFile): Boolean

    /**
     * Allows to transform suggested imports list by any rule.
     */
    fun filterSuggestions(suggestions: Collection<FqName>): Collection<FqName>

    companion object {
        val EP_NAME = ExtensionPointName.create<KotlinAutoImportsFilter>("org.jetbrains.kotlin.idea.quickfix.unambiguousImports")

        fun findRelevantExtension(file: KtFile): KotlinAutoImportsFilter? = EP_NAME.findFirstSafe { it.forceAutoImportForFile(file) }
    }
}