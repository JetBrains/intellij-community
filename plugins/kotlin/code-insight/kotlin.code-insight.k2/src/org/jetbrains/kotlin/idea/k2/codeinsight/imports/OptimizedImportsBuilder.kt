// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassifierSymbol
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.imports.KotlinImportPathComparator
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.imports.ImportMapper
import org.jetbrains.kotlin.idea.imports.KotlinIdeDefaultImportProvider
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtImportDirective
import org.jetbrains.kotlin.resolve.ImportPath

internal fun KaSession.buildOptimizedImports(
    file: KtFile,
    data: UsedReferencesCollector.Result,
): List<ImportPath>? {
    return OptimizedImportsBuilder(file, data, file.kotlinCustomSettings).run { buildOptimizedImports() }
}

internal class OptimizedImportsBuilder(
    private val file: KtFile,
    private val usedReferencesData: UsedReferencesCollector.Result,
    private val codeStyleSettings: KotlinCodeStyleSettings,
) {
    private val apiVersion: ApiVersion = file.languageVersionSettings.apiVersion

    private val importPaths: Set<ImportPath> by lazy {
        file.importDirectives.mapNotNull { it.importPath }.toSet()
    }

    private sealed class ImportRule {
        // force presence of this import
        data class Add(val importPath: ImportPath) : ImportRule() {
            override fun toString() = "+$importPath"
        }

        // force absence of this import
        data class DoNotAdd(val importPath: ImportPath) : ImportRule() {
            override fun toString() = "-$importPath"
        }
    }

    private val importRules: MutableSet<ImportRule> = mutableSetOf()

    fun KaSession.buildOptimizedImports(): List<ImportPath>? {
        require(importRules.isEmpty())

        val importsWithUnresolvedNames = file.importDirectives
            .filter { it.mayReferToSomeUnresolvedName() || it.isExistedUnresolvedName() }
            .mapNotNull { it.importPath }

        importRules += importsWithUnresolvedNames.map { ImportRule.Add(it) }

        while (true) {
            ProgressManager.checkCanceled()
            val importRulesBefore = importRules.size
            val result = tryBuildOptimizedImports()
            if (importRules.size == importRulesBefore) return result
        }
    }

    fun KaSession.tryBuildOptimizedImports(): List<ImportPath>? {
        val importsToGenerate = hashSetOf<ImportPath>()
        importsToGenerate += importRules.filterIsInstance<ImportRule.Add>().map { it.importPath }

        val importableSymbols = usedReferencesData.usedSymbols.mapNotNull { it.run { restore() } }

        val symbolsByParentFqName = HashMap<FqName, MutableSet<SymbolInfo>>()
        for (importableSymbol in importableSymbols) {
            val fqName = importableSymbol.run { computeImportableName() } ?: continue
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

            val fqNames = symbols.mapNotNull { importSymbolWithMapping(it) }.toSet()

            val nameCountToUseStar = nameCountToUseStar(symbols.first())
            val useExplicitImports = !starImportPath.isAllowedByRules() || (fqNames.size < nameCountToUseStar && !parentFqName.isInPackagesToUseStarImport())

            if (useExplicitImports) {
                fqNames.filter { fqName -> needExplicitImport(fqName) }.mapTo(importsToGenerate) { ImportPath(it, isAllUnder = false) }
            } else {
                symbols.asSequence()
                    .filter { it is ClassLikeSymbolInfo }
                    .mapNotNull { importSymbolWithMapping(it) }
                    .filterTo(classNamesToCheck) { needExplicitImport(it) }

                if (fqNames.all { needExplicitImport(it) }) {
                    importsToGenerate.add(starImportPath)
                }
            }
        }

        val importingScopes = buildImportingScopes(file, importsToGenerate.filter { it.isAllUnder })
        val hierarchicalScope = HierarchicalScope.run { createFrom(importingScopes) }

        for (fqName in classNamesToCheck) {
            val foundClassifiers = hierarchicalScope.findClassifiers(fqName.shortName()).firstOrNull()
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

        detectConflictsAndUpdateRules(sortedImportsToGenerate)

        return sortedImportsToGenerate
    }

    private fun detectConflictsAndUpdateRules(newImports: List<ImportPath>) {
        val fileWithReplacedImports = KtFileWithReplacedImports.createFrom(file)
        val referencesMap = KtReferencesInCopyMap.createFor(file, fileWithReplacedImports.ktFile)

        fileWithReplacedImports.setImports(newImports)

        for ((names, references) in usedReferencesData.references.groupBy { it.resolvesByNames }) {
            // TODO check if new and old scopes contain different symbols for names set
            fileWithReplacedImports.analyze {
                for (originalReference in references) {
                    val alternativeReference = referencesMap.findReferenceInCopy(originalReference)

                    val originalUsedReference = UsedReference.run { createFrom(originalReference) }
                    val alternativeUsedReference = UsedReference.run { createFrom(alternativeReference) }

                    val originalSymbols = originalUsedReference?.run { resolveToReferencedSymbols() }
                    val alternativeSymbols = alternativeUsedReference?.run { resolveToReferencedSymbols() }

                    if (!areTargetsEqual(originalSymbols, alternativeSymbols)) {
                        val conflictingSymbols = originalSymbols.orEmpty() + alternativeSymbols.orEmpty()

                        for (conflictingSymbol in conflictingSymbols) {
                            lockImportForSymbol(conflictingSymbol.run { toSymbolInfo() }, names)
                        }
                    }
                }
            }
        }
    }

    private fun KaSession.areTargetsEqual(
        originalSymbols: Collection<ReferencedSymbol>?,
        alternativeSymbols: Collection<ReferencedSymbol>?
    ): Boolean {
        if (originalSymbols == null || alternativeSymbols == null) {
            return originalSymbols == alternativeSymbols
        }

        if (originalSymbols.size != alternativeSymbols.size) return false

        return originalSymbols.zip(alternativeSymbols).all { (originalSymbol, newSymbol) -> areTargetsEqual(originalSymbol, newSymbol) }
    }

    private fun KaSession.areTargetsEqual(
        originalSymbol: ReferencedSymbol,
        alternativeSymbol: ReferencedSymbol,
    ): Boolean {
        val originalSymbol = originalSymbol.run { toSymbolInfo() }
        val newSymbol = alternativeSymbol.run { toSymbolInfo() }

        return originalSymbol == newSymbol ||
                importSymbolWithMapping(originalSymbol) == importSymbolWithMapping(newSymbol)
    }

    private fun KaSession.lockImportForSymbol(symbol: SymbolInfo, existingNames: Collection<Name>) {
        val name = symbol.run { computeImportableName() }?.shortName() ?: return
        val fqName = importSymbolWithMapping(symbol) ?: return
        val names = usedReferencesData.usedDeclarations.getOrElse(fqName) { listOf(name) }.intersect(existingNames.toSet())

        val starImportPath = ImportPath(fqName.parent(), true)
        for (name in names) {
            val alias = if (name != fqName.shortName()) name else null
            val explicitImportPath = ImportPath(fqName, false, alias)
            when {
                explicitImportPath in importPaths ->
                    importRules.add(ImportRule.Add(explicitImportPath))
                alias == null && starImportPath in importPaths ->
                    importRules.add(ImportRule.Add(starImportPath))
                else -> // there is no import for this descriptor in the original import list, so do not allow to import it by star-import
                    importRules.add(ImportRule.DoNotAdd(starImportPath))
            }
        }
    }

    private fun KtImportDirective.mayReferToSomeUnresolvedName() =
        isAllUnder && usedReferencesData.unresolvedNames.isNotEmpty()

    private fun KtImportDirective.isExistedUnresolvedName() = importedName in usedReferencesData.unresolvedNames

    private fun findCorrespondingKotlinFqName(fqName: FqName): FqName? {
        return ImportMapper.findCorrespondingKotlinFqName(fqName, apiVersion)
    }

    private fun KaSession.importSymbolWithMapping(symbol: SymbolInfo): FqName? {
        val importableName = symbol.run { computeImportableName() } ?: return null

        return findCorrespondingKotlinFqName(importableName) ?: importableName
    }

    private fun KaSession.canUseStarImport(importableSymbol: SymbolInfo, fqName: FqName): Boolean = when {
        fqName.parent().isRoot -> false
        // star import from objects is not allowed
        (importableSymbol.run { containingClassSymbol() } as? KaClassSymbol)?.classKind?.isObject == true -> false
        else -> true
    }

    private fun KaSession.nameCountToUseStar(symbol: SymbolInfo): Int {
        if (symbol.run { containingClassSymbol() } == null) {
            return codeStyleSettings.NAME_COUNT_TO_USE_STAR_IMPORT
        } else {
            return codeStyleSettings.NAME_COUNT_TO_USE_STAR_IMPORT_FOR_MEMBERS
        }
    }

    private fun FqName.isInPackagesToUseStarImport(): Boolean {
        return this.toString() in codeStyleSettings.PACKAGES_TO_USE_STAR_IMPORTS
    }

    private fun ImportPath.isAllowedByRules(): Boolean = importRules.none { it is ImportRule.DoNotAdd && it.importPath == this }

    private fun needExplicitImport(fqName: FqName): Boolean = hasAlias(fqName) || !isImportedByDefault(fqName)

    private fun hasAlias(fqName: FqName): Boolean = usedReferencesData.usedDeclarations[fqName].orEmpty().size > 1

    private fun isImportedByDefault(fqName: FqName): Boolean =
        KotlinIdeDefaultImportProvider.getInstance().isImportedWithDefault(ImportPath(fqName, isAllUnder = false), file)
}

