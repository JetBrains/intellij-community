// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.imports

import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.idea.inspections.KotlinUnusedImportInspection
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.resolve.ImportPath

internal class K1OptimizeImportsFacility : KotlinOptimizeImportsFacility {

    private class K1ImportData(
        override val unusedImports: List<KtImportDirective>,
        val optimizerData: OptimizedImportsBuilder.InputData,
    ) : KotlinOptimizeImportsFacility.ImportData

    override fun analyzeImports(file: KtFile): KotlinOptimizeImportsFacility.ImportData? {
        val inputData = KotlinUnusedImportInspection.analyzeImports(file) ?: return null

        return K1ImportData(inputData.unusedImports, inputData.optimizerData)
    }

    override fun prepareOptimizedImports(
        file: KtFile,
        data: KotlinOptimizeImportsFacility.ImportData,
    ): List<ImportPath>? {
        require(data is K1ImportData)

        return KotlinImportOptimizer.prepareOptimizedImports(file, data.optimizerData)
    }
}
