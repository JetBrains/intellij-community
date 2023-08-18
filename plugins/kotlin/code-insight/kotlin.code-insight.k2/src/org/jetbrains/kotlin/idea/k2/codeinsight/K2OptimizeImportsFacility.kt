// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.resolve.ImportPath

internal class K2OptimizeImportsFacility : KotlinOptimizeImportsFacility {
    private class K2ImportData(override val unusedImports: List<KtImportDirective>) : KotlinOptimizeImportsFacility.ImportData

    override fun analyzeImports(file: KtFile): KotlinOptimizeImportsFacility.ImportData? {
        // Import optimizer might be called from reformat action in EDT, see KTIJ-25031
        @OptIn(KtAllowAnalysisOnEdt::class)
        val unusedImports = allowAnalysisOnEdt {
            analyze(file) {
                analyseImports(file).unusedImports
            }
        }

        return K2ImportData(unusedImports.toList())
    }

    override fun prepareOptimizedImports(
        file: KtFile,
        data: KotlinOptimizeImportsFacility.ImportData,
    ): List<ImportPath>? {
        require(data is K2ImportData)

        if (data.unusedImports.isEmpty()) return null
        return file.importDirectives.filterNot { it in data.unusedImports }.mapNotNull { it.importPath }
    }
}