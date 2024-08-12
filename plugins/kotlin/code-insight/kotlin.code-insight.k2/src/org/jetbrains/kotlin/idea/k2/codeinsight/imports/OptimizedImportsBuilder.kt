// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.imports.KotlinImportPathComparator
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.imports.ImportMapper
import org.jetbrains.kotlin.idea.imports.KotlinIdeDefaultImportProvider
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.resolve.ImportPath

internal fun KaSession.buildOptimizedImports(
    file: KtFile,
    data: UsedReferencesCollector.Result,
): List<ImportPath>? {
    return OptimizedImportsBuilder(file, data, file.kotlinCustomSettings).run { build() }
}

internal class OptimizedImportsBuilder(
    private val file: KtFile,
    private val usedReferencesData: UsedReferencesCollector.Result,
    private val codeStyleSettings: KotlinCodeStyleSettings,
) {
    private val apiVersion: ApiVersion = file.languageVersionSettings.apiVersion

    fun KaSession.build(): List<ImportPath>? {
        val importsToGenerate = hashSetOf<ImportPath>()
        val importableSymbols = usedReferencesData.usedSymbols.mapNotNull { it.run { restore() } }

        val importsWithUnresolvedNames = file.importDirectives
            .filter { it.mayReferToSomeUnresolvedName() || it.isExistedUnresolvedName() }

        importsToGenerate += importsWithUnresolvedNames.mapNotNull { it.importPath }

        val symbolsByParentFqName = HashMap<FqName, MutableSet<ImportableKaSymbol>>()
        for (importableSymbol in importableSymbols) {
            val fqName = importableSymbol.run { computeImportableName() }
            for (name in usedReferencesData.usedDeclarations.getValue(fqName)) {
                val alias = if (name != fqName.shortName()) name else null

                val resultFqName = findCorrespondingKotlinFqName(fqName) ?: fqName
                val explicitImportPath = ImportPath(resultFqName, isAllUnder = false, alias)
                if (explicitImportPath in importsToGenerate) continue

                val parentFqName = resultFqName.parent()
                if (
                    alias == null &&
                    canUseStarImport(importableSymbol, resultFqName) &&
                    ImportPath(parentFqName, isAllUnder = true).isAllowedByRules()
                ) {
                    symbolsByParentFqName.getOrPut(parentFqName) { hashSetOf() }.add(importableSymbol)
                } else {
                    importsToGenerate.add(explicitImportPath)
                }
            }
        }

        val classNamesToCheck = hashSetOf<FqName>()
        for ((parentFqName, symbols) in symbolsByParentFqName) {
            ProgressManager.checkCanceled()

            val starImportPath = ImportPath(parentFqName, isAllUnder = true)
            if (starImportPath in importsToGenerate) continue

            val fqNames = symbols.map { importSymbolWithMapping(it) }.toSet()

            val nameCountToUseStar = nameCountToUseStar(symbols.first())
            val useExplicitImports = !starImportPath.isAllowedByRules() || (fqNames.size < nameCountToUseStar && !parentFqName.isInPackagesToUseStarImport())

            if (useExplicitImports) {
                fqNames.filter { fqName -> needExplicitImport(fqName) }.mapTo(importsToGenerate) { ImportPath(it, isAllUnder = false) }
            } else {
                symbols.asSequence()
                    .filter { it is ImportableKaClassLikeSymbol }
                    .map { importSymbolWithMapping(it) }
                    .filterTo(classNamesToCheck) { needExplicitImport(it) }

                if (fqNames.all { needExplicitImport(it) }) {
                    importsToGenerate.add(starImportPath)
                }
            }
        }

        val importingScopeContext = buildScopeContextByImports(file, importsToGenerate.filter { it.isAllUnder })
        val importingScopes = HierarchicalScope.run { createFrom(importingScopeContext) }

        for (fqName in classNamesToCheck) {
            val foundClassifiers = importingScopes.findClassifiers(fqName.shortName()).firstOrNull()
            val singleFoundClassifier = foundClassifiers?.singleOrNull()

            if (singleFoundClassifier?.importableFqName != fqName) {
                // add explicit import if failed to import with * (or from current package)
                importsToGenerate.add(ImportPath(fqName, false))

                val parentFqName = fqName.parent()

                val siblingsToImport = symbolsByParentFqName.getValue(parentFqName)
                for (descriptor in siblingsToImport.filter { it.run { computeImportableName() } == fqName }) {
                    siblingsToImport.remove(descriptor)
                }

                if (siblingsToImport.isEmpty()) { // star import is not really needed
                    importsToGenerate.remove(ImportPath(parentFqName, true))
                }
            }
        }

        val sortedImportsToGenerate = importsToGenerate.sortedWith(KotlinImportPathComparator.create(file))

        val oldImports = file.importDirectives
        if (oldImports.size == sortedImportsToGenerate.size && oldImports.map { it.importPath } == sortedImportsToGenerate) return null

        return sortedImportsToGenerate
    }

    private fun KtImportDirective.mayReferToSomeUnresolvedName() =
        isAllUnder && usedReferencesData.unresolvedNames.isNotEmpty()

    private fun KtImportDirective.isExistedUnresolvedName() = importedName in usedReferencesData.unresolvedNames

    private fun findCorrespondingKotlinFqName(fqName: FqName): FqName? {
        return ImportMapper.findCorrespondingKotlinFqName(fqName, apiVersion)
    }

    private fun KaSession.importSymbolWithMapping(symbol: ImportableKaSymbol): FqName {
        val importableName = symbol.run { computeImportableName() }

        return findCorrespondingKotlinFqName(importableName) ?: importableName
    }

    private fun KaSession.canUseStarImport(importableSymbol: ImportableKaSymbol, fqName: FqName): Boolean = when {
        fqName.parent().isRoot -> false
        // star import from objects is not allowed
        (importableSymbol.run { containingClassSymbol() } as? KaClassSymbol)?.classKind?.isObject == true -> false
        else -> true
    }

    private fun KaSession.nameCountToUseStar(symbol: ImportableKaSymbol): Int {
        if (symbol.run { containingClassSymbol() } == null) {
            return codeStyleSettings.NAME_COUNT_TO_USE_STAR_IMPORT
        } else {
            return codeStyleSettings.NAME_COUNT_TO_USE_STAR_IMPORT_FOR_MEMBERS
        }
    }

    private fun FqName.isInPackagesToUseStarImport(): Boolean {
        return this.toString() in codeStyleSettings.PACKAGES_TO_USE_STAR_IMPORTS
    }

    private fun ImportPath.isAllowedByRules(): Boolean {
        // trivial implementation until proper conflicts resolution is implemented
        return true
    }

    private fun needExplicitImport(fqName: FqName): Boolean = hasAlias(fqName) || !isImportedByDefault(fqName)

    private fun hasAlias(fqName: FqName): Boolean = usedReferencesData.usedDeclarations[fqName].orEmpty().size > 1

    private fun isImportedByDefault(fqName: FqName): Boolean =
        KotlinIdeDefaultImportProvider.getInstance().isImportedWithDefault(ImportPath(fqName, isAllUnder = false), file)
}

