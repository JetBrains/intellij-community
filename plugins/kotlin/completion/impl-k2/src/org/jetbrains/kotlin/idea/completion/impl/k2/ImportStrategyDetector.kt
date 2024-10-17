// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.completion.impl.k2

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getDefaultImports
import org.jetbrains.kotlin.idea.base.utils.fqname.isImported
import org.jetbrains.kotlin.idea.completion.lookups.ImportStrategy
import org.jetbrains.kotlin.idea.completion.lookups.isExtensionCall
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath

@ApiStatus.Internal
class ImportStrategyDetector(originalKtFile: KtFile, project: Project) {
    private val defaultImports: Set<ImportPath>
    private val excludedImports: List<FqName>

    init {
        val imports = originalKtFile.getDefaultImports(useSiteModule = null)
        defaultImports = imports.defaultImports.mapTo(mutableSetOf()) { it.importPath }
        excludedImports = imports.excludedFromDefaultImports.map { it.fqName }
    }


    context(KaSession)
    fun detectImportStrategyForCallableSymbol(symbol: KaCallableSymbol, isFunctionalVariableCall: Boolean = false): ImportStrategy {
        val containingClassIsObject = (symbol.fakeOverrideOriginal.containingSymbol as? KaClassSymbol)?.classKind?.isObject == true
        if (symbol.location == KaSymbolLocation.CLASS && !containingClassIsObject) return ImportStrategy.DoNothing

        val callableId = symbol.callableId?.asSingleFqName() ?: return ImportStrategy.DoNothing

        return if (symbol.isExtensionCall(isFunctionalVariableCall)) {
            ImportStrategy.AddImport(callableId)
        } else {
            ImportStrategy.InsertFqNameAndShorten(callableId)
        }
    }

    context (KaSession)
    fun detectImportStrategyForClassifierSymbol(symbol: KaClassifierSymbol): ImportStrategy {
        if (symbol !is KaClassLikeSymbol) return ImportStrategy.DoNothing

        val classId = symbol.classId?.asSingleFqName() ?: return ImportStrategy.DoNothing
        return ImportStrategy.InsertFqNameAndShorten(classId)
    }

    fun FqName.isAlreadyImported(): Boolean {
        val importPath = ImportPath(this, isAllUnder = false)
        return importPath.isImported(defaultImports, excludedImports)
    }
}