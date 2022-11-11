// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.utils.fqname.isImported
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath

@ApiStatus.Internal
class ImportStrategyDetector(originalKtFile: KtFile, project: Project) {
    private val analyzerServices = originalKtFile.platform.findAnalyzerServices(project)
    private val defaultImports = analyzerServices
        .getDefaultImports(originalKtFile.languageVersionSettings, includeLowPriorityImports = true).toSet()

    private val excludedImports = analyzerServices.excludedImports

    context (KtAnalysisSession)
    internal fun detectImportStrategy(symbol: KtSymbol): ImportStrategy = when (symbol) {
        is KtCallableSymbol -> detectImportStrategyForCallableSymbol(symbol)
        is KtClassLikeSymbol -> detectImportStrategyForClassLikeSymbol(symbol)
        else -> ImportStrategy.DoNothing
    }

    context(KtAnalysisSession)
    private fun detectImportStrategyForCallableSymbol(symbol: KtCallableSymbol): ImportStrategy {
        if (symbol.symbolKind == KtSymbolKind.CLASS_MEMBER) return ImportStrategy.DoNothing

        val callableId = symbol.callableIdIfNonLocal?.asSingleFqName() ?: return ImportStrategy.DoNothing
        if (callableId.isAlreadyImported()) return ImportStrategy.DoNothing

        return if (symbol.isExtension) {
            ImportStrategy.AddImport(callableId)
        } else {
            ImportStrategy.InsertFqNameAndShorten(callableId)
        }
    }

    context (KtAnalysisSession)
    private fun detectImportStrategyForClassLikeSymbol(symbol: KtClassLikeSymbol): ImportStrategy {
        val classId = symbol.classIdIfNonLocal?.asSingleFqName() ?: return ImportStrategy.DoNothing
        if (classId.isAlreadyImported()) return ImportStrategy.DoNothing
        return ImportStrategy.InsertFqNameAndShorten(classId)
    }

    private fun FqName.isAlreadyImported(): Boolean {
        val importPath = ImportPath(this, isAllUnder = false)
        return importPath.isImported(defaultImports, excludedImports)
    }
}