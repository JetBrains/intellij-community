// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KtImportOptimizerResult
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.idea.base.psi.imports.KotlinImportPathComparator
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.resolve.ImportPath

internal class K2OptimizeImportsFacility : KotlinOptimizeImportsFacility {
    private class K2ImportData(override val unusedImports: List<KtImportDirective>) : KotlinOptimizeImportsFacility.ImportData

    override fun analyzeImports(file: KtFile): KotlinOptimizeImportsFacility.ImportData? {
        // Import optimizer might be called from reformat action in EDT, see KTIJ-25031
        @OptIn(KtAllowAnalysisOnEdt::class)
        val importAnalysis = allowAnalysisOnEdt {
            // Import optimizer might invoke be from write action in refactorings
            @OptIn(KtAllowAnalysisFromWriteAction::class)
            allowAnalysisFromWriteAction {
                analyze(file) {
                    analyseImports(file)
                }
            }
        }

        val unusedImports = computeUnusedImports(file, importAnalysis)
        return K2ImportData(unusedImports.toList())
    }

    private fun computeUnusedImports(file: KtFile, result: KtImportOptimizerResult): Set<KtImportDirective> {
        val existingImports = file.importDirectives
        if (existingImports.isEmpty()) return emptySet()

        val explicitlyImportedFqNames = existingImports
            .asSequence()
            .mapNotNull { it.importPath }
            .filter { !it.isAllUnder && !it.hasAlias() }
            .map { it.fqName }
            .toSet()

        val (usedImports, unresolvedNames) = result.usedDeclarations to result.unresolvedNames

        val referencesEntities = usedImports
            .filterNot { (fqName, referencedByNames) ->
                val fromCurrentPackage = fqName.parentOrNull() == file.packageFqName
                val noAliasedImports = referencedByNames.singleOrNull() == fqName.shortName()

                fromCurrentPackage && noAliasedImports
            }

        val requiredStarImports = referencesEntities.keys
            .asSequence()
            .filterNot { it in explicitlyImportedFqNames }
            .mapNotNull { it.parentOrNull() }
            .filterNot { it.isRoot }
            .toSet()

        val unusedImports = mutableSetOf<KtImportDirective>()
        val alreadySeenImports = mutableSetOf<ImportPath>()

        for (import in existingImports) {
            val importPath = import.importPath ?: continue

            val isUsed = when {
                importPath.importedName in unresolvedNames -> true
                !alreadySeenImports.add(importPath) -> false
                importPath.isAllUnder -> unresolvedNames.isNotEmpty() || importPath.fqName in requiredStarImports
                importPath.fqName in referencesEntities -> importPath.importedName in referencesEntities.getValue(importPath.fqName)
                else -> false
            }

            if (!isUsed) {
                unusedImports += import
            }
        }

        return unusedImports
    }

    override fun prepareOptimizedImports(
        file: KtFile,
        data: KotlinOptimizeImportsFacility.ImportData,
    ): List<ImportPath>? {
        require(data is K2ImportData)

        val usedImports = (file.importDirectives - data.unusedImports).mapNotNull { it.importPath }
        val sortedUsedImports = usedImports.sortedWith(KotlinImportPathComparator.create(file))

        if (data.unusedImports.isEmpty() && usedImports == sortedUsedImports) {
            // Imports did not change, do nothing
            return null
        }

        return sortedUsedImports
    }
}