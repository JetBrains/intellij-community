// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.lookups

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
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

@OptIn(KaExperimentalApi::class)
private fun alreadyHasImport(file: KtFile, nameToImport: FqName): Boolean {
    if (file.importDirectives.any { it.importPath?.fqName == nameToImport }) return true

    withAllowedResolve {
        analyze(file) {
            val scope = file.importingScopeContext.compositeScope()
            if (!scope.mayContainName(nameToImport.shortName())) return false

            val anyCallableSymbolMatches = scope
                .callables(nameToImport.shortName())
                .any { callable ->
                    val callableFqName = callable.callableId?.asSingleFqName()
                    callable is KaKotlinPropertySymbol && callableFqName == nameToImport ||
                            callable is KaNamedFunctionSymbol && callableFqName == nameToImport
                }
            if (anyCallableSymbolMatches) return true

            return scope.classifiers(nameToImport.shortName()).any { classifier ->
                val classId = (classifier as? KaClassLikeSymbol)?.classId
                classId?.asSingleFqName() == nameToImport
            }
        }
    }
}