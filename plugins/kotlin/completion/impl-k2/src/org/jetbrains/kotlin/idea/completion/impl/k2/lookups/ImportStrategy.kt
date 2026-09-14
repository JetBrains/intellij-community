// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.completion.impl.k2.lookups

import com.intellij.codeInsight.completion.InsertionContext
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.compositeScope
import org.jetbrains.kotlin.analysis.api.components.importingScopeContext
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaKotlinPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.serialization.names.KotlinFqNameSerializer
import org.jetbrains.kotlin.idea.completion.impl.k2.doPostponedOperationsAndUnblockDocument
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
@Serializable
sealed class ImportStrategy {
    @Serializable
    object DoNothing : ImportStrategy()

    @Serializable
    data class AddImport(
        @Serializable(with = KotlinFqNameSerializer::class) val nameToImport: FqName
    ) : ImportStrategy()

    @Serializable
    data class InsertFqNameAndShorten(
        @Serializable(with = KotlinFqNameSerializer::class) val fqName: FqName
    ) : ImportStrategy()
}

internal fun addImportIfRequired(
    context: InsertionContext,
    nameToImport: FqName
) {
    val targetFile = context.file as KtFile
    if (alreadyHasImport(targetFile, nameToImport)) return

    targetFile.addImport(nameToImport)
    context.doPostponedOperationsAndUnblockDocument()
}

internal fun alreadyHasImport(file: KtFile, nameToImport: FqName): Boolean {
    if (hasImportDirective(file, nameToImport)) return true

    withAllowedResolve {
        analyze(file) {
            return importingScope(file).hasDeclaration(nameToImport)
        }
    }
}

internal fun hasImportDirective(file: KtFile, nameToImport: FqName): Boolean {
    return file.importDirectives.any { it.importPath?.fqName == nameToImport }
}

/**
 * The scope [alreadyHasImport] looks in: explicit, default and package imports of [file]. It depends on the file only,
 * so a caller that checks many names builds it once per analysis.
 */
context(_: KaSession)
internal fun importingScope(file: KtFile): KaScope = file.importingScopeContext.compositeScope()

/** Whether this importing scope has a property, a function or a classifier of [nameToImport]. */
context(_: KaSession)
internal fun KaScope.hasDeclaration(nameToImport: FqName): Boolean {
    if (!mayContainName(nameToImport.shortName())) return false

    val anyCallableSymbolMatches = callables(nameToImport.shortName())
        .any { callable ->
            val callableFqName = callable.callableId?.asSingleFqName()
            callable is KaKotlinPropertySymbol && callableFqName == nameToImport ||
                    callable is KaNamedFunctionSymbol && callableFqName == nameToImport
        }
    if (anyCallableSymbolMatches) return true

    return classifiers(nameToImport.shortName()).any { classifier ->
        val classId = (classifier as? KaClassLikeSymbol)?.classId
        classId?.asSingleFqName() == nameToImport
    }
}
