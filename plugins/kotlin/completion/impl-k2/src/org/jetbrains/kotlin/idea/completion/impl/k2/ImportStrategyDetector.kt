// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtClassifierSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolKind
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.utils.fqname.isImported
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.isExtensionCall
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath

@ApiStatus.Internal
class ImportStrategyDetector(originalKtFile: KtFile, project: Project) {
    private val analyzerServices = originalKtFile.platform.findAnalyzerServices(project)
    private val defaultImports = analyzerServices
        .getDefaultImports(originalKtFile.languageVersionSettings, includeLowPriorityImports = true).toSet()

    private val excludedImports = analyzerServices.excludedImports

    context(KtAnalysisSession)
    fun detectImportStrategyForCallableSymbol(symbol: KtCallableSymbol, isFunctionalVariableCall: Boolean = false): ImportStrategy {
        val containingClassIsObject = symbol.originalContainingClassForOverride?.classKind?.isObject == true
        if (symbol.symbolKind == KtSymbolKind.CLASS_MEMBER && !containingClassIsObject) return ImportStrategy.DoNothing

        val callableId = symbol.callableIdIfNonLocal?.asSingleFqName() ?: return ImportStrategy.DoNothing

        return if (symbol.isExtensionCall(isFunctionalVariableCall)) {
            ImportStrategy.AddImport(callableId)
        } else {
            ImportStrategy.InsertFqNameAndShorten(callableId)
        }
    }

    context (KtAnalysisSession)
    fun detectImportStrategyForClassifierSymbol(symbol: KtClassifierSymbol): ImportStrategy {
        if (symbol !is KtClassLikeSymbol) return ImportStrategy.DoNothing

        val classId = symbol.classIdIfNonLocal?.asSingleFqName() ?: return ImportStrategy.DoNothing
        return ImportStrategy.InsertFqNameAndShorten(classId)
    }

    fun FqName.isAlreadyImported(): Boolean {
        val importPath = ImportPath(this, isAllUnder = false)
        return importPath.isImported(defaultImports, excludedImports)
    }
}