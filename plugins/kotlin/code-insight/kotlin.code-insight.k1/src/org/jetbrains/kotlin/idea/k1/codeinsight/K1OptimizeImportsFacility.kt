// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k1.codeinsight

import org.jetbrains.kotlin.idea.base.codeInsight.KotlinOptimizeImportsFacility
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.core.targetDescriptors
import org.jetbrains.kotlin.idea.imports.KotlinImportOptimizer
import org.jetbrains.kotlin.idea.imports.OptimizedImportsBuilder
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.idea.references.KtInvokeFunctionReference
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.resolve.ImportPath

internal class K1OptimizeImportsFacility : KotlinOptimizeImportsFacility {

    private class K1ImportData(
        override val unusedImports: List<KtImportDirective>,
        val optimizerData: OptimizedImportsBuilder.InputData,
    ) : KotlinOptimizeImportsFacility.ImportData

    override fun analyzeImports(file: KtFile): KotlinOptimizeImportsFacility.ImportData? {
        if (file.importDirectives.isEmpty()) return null

        val optimizerData = KotlinImportOptimizer.collectDescriptorsToImport(file)

        val directives = file.importDirectives
        val explicitlyImportedFqNames = directives
            .asSequence()
            .mapNotNull { it.importPath }
            .filter { !it.isAllUnder && !it.hasAlias() }
            .map { it.fqName }
            .toSet()

        val fqNames = optimizerData.namesToImport
        val parentFqNames = HashSet<FqName>()
        for ((_, fqName) in optimizerData.descriptorsToImport) {
            // we don't add parents of explicitly imported fq-names because such imports are not needed
            if (fqName in explicitlyImportedFqNames) continue
            val parentFqName = fqName.parent()
            if (!parentFqName.isRoot) {
                parentFqNames.add(parentFqName)
            }
        }

        val invokeFunctionCallFqNames = optimizerData.references.mapNotNull {
            val reference = (it.element as? KtCallExpression)?.mainReference as? KtInvokeFunctionReference ?: return@mapNotNull null
            (reference.resolve() as? KtNamedFunction)?.descriptor?.importableFqName
        }

        val importPaths = HashSet<ImportPath>(directives.size)
        val unusedImports = ArrayList<KtImportDirective>()

        val resolutionFacade = file.getResolutionFacade()
        for (directive in directives) {
            val importPath = directive.importPath ?: continue

            val isUsed = when {
                importPath.importedName in optimizerData.unresolvedNames -> true
                !importPaths.add(importPath) -> false
                importPath.isAllUnder -> optimizerData.unresolvedNames.isNotEmpty() || importPath.fqName in parentFqNames
                importPath.fqName in fqNames -> importPath.importedName?.let { it in fqNames.getValue(importPath.fqName) } ?: false
                importPath.fqName in invokeFunctionCallFqNames -> true
                // case for type alias
                else -> directive.targetDescriptors(resolutionFacade).firstOrNull()?.let { it.importableFqName in fqNames } ?: false
            }

            if (!isUsed) {
                unusedImports += directive
            }
        }

        return K1ImportData(unusedImports, optimizerData)
    }

    override fun prepareOptimizedImports(
        file: KtFile,
        data: KotlinOptimizeImportsFacility.ImportData,
    ): List<ImportPath>? {
        require(data is K1ImportData)

        return KotlinImportOptimizer.prepareOptimizedImports(file, data.optimizerData)
    }
}
