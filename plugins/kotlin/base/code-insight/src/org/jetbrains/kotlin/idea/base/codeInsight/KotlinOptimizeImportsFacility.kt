// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight

import com.intellij.openapi.components.service
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * Temporary service to unify implementations of "Unused import" inspections.
 */
interface KotlinOptimizeImportsFacility {
    interface ImportData {
        val unusedImports: List<KtImportDirective>
    }

    fun analyzeImports(file: KtFile): ImportData?

    /**
     * Returns `null` if the imports are already optimized and don't need to be edited.
     */
    fun prepareOptimizedImports(file: KtFile, data: ImportData): List<ImportPath>?

    companion object {
        fun getInstance(): KotlinOptimizeImportsFacility = service()
    }
}