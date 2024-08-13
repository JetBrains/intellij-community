// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight

import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisFromWriteAction
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.projectStructure.KaNotUnderContentRootModule
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo
import org.jetbrains.kotlin.idea.base.projectStructure.toKaModule
import org.jetbrains.kotlin.idea.k2.codeinsight.imports.UsedReferencesCollector
import org.jetbrains.kotlin.idea.k2.codeinsight.imports.buildOptimizedImports
import org.jetbrains.kotlin.name.parentOrNull
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.resolve.ImportPath

internal class K2OptimizeImportsFacility : KotlinOptimizeImportsFacility {
    private class K2ImportData(
        override val unusedImports: List<KtImportDirective>,
        val usedReferencesData: UsedReferencesCollector.Result,
    ) : KotlinOptimizeImportsFacility.ImportData

    /**
     * Import optimizer might be invoked from write action and EDT in refactorings and reformat action, see KTIJ-25031.
     *
     * So we have to allow the resolve from under write action and from EDT.
     */
    @OptIn(KaAllowAnalysisOnEdt::class, KaAllowAnalysisFromWriteAction::class)
    private fun <T> analyzeForImportOptimization(file: KtFile, action: KaSession.() -> T): T =
        allowAnalysisOnEdt {
            allowAnalysisFromWriteAction {
                analyze(file) {
                    action()
                }
            }
        }

    override fun analyzeImports(file: KtFile): KotlinOptimizeImportsFacility.ImportData? {
        if (!canOptimizeImports(file)) return null

        val importAnalysis = analyzeForImportOptimization(file) {
            val referenceCollector = UsedReferencesCollector(file)
            referenceCollector.run { collectUsedReferences() }
        }

        val unusedImports = computeUnusedImports(file, importAnalysis)
        return K2ImportData(unusedImports.toList(), importAnalysis)
    }

    @OptIn(KaPlatformInterface::class)
    private fun canOptimizeImports(file: KtFile): Boolean {
        val module = file.moduleInfo.toKaModule()

        // it does not make sense to optimize imports in files
        // which are not under content roots (like testdata)
        return module !is KaNotUnderContentRootModule
    }

    private fun computeUnusedImports(file: KtFile, result: UsedReferencesCollector.Result): Set<KtImportDirective> {
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

        return analyzeForImportOptimization(file) {
            buildOptimizedImports(file, data.usedReferencesData)
        }
    }
}