// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KtClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtKotlinPropertySymbol
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
sealed class ImportStrategy {
    object DoNothing : ImportStrategy()
    data class AddImport(val nameToImport: FqName) : ImportStrategy()
    data class InsertFqNameAndShorten(val fqName: FqName) : ImportStrategy()
}

internal fun addImportIfRequired(targetFile: KtFile, nameToImport: FqName) {
    if (!alreadyHasImport(targetFile, nameToImport)) {
        targetFile.addImport(nameToImport)
    }
}

private fun alreadyHasImport(file: KtFile, nameToImport: FqName): Boolean {
    if (file.importDirectives.any { it.importPath?.fqName == nameToImport }) return true

    withAllowedResolve {
        analyze(file) {
            val scope = file.getImportingScopeContext().getCompositeScope()
            if (!scope.mayContainName(nameToImport.shortName())) return false

            val anyCallableSymbolMatches = scope
                .getCallableSymbols(nameToImport.shortName())
                .any { callable ->
                    val callableFqName = callable.callableIdIfNonLocal?.asSingleFqName()
                    callable is KtKotlinPropertySymbol && callableFqName == nameToImport ||
                            callable is KtFunctionSymbol && callableFqName == nameToImport
                }
            if (anyCallableSymbolMatches) return true

            return scope.getClassifierSymbols(nameToImport.shortName()).any { classifier ->
                val classId = (classifier as? KtClassLikeSymbol)?.classIdIfNonLocal
                classId?.asSingleFqName() == nameToImport
            }
        }
    }
}