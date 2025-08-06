// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import org.jetbrains.kotlin.analysis.api.KaImplementationDetail
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.*
import org.jetbrains.kotlin.analysis.api.impl.base.components.KaBaseIllegalPsiException
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaClassLikeSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbol
import org.jetbrains.kotlin.analysis.api.symbols.findPackage
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath

context(_: KaSession)
internal fun buildImportingScopes(originalFile: KtFile, imports: Collection<ImportPath>): List<KaScopeWithKind> {
    val fileWithImports = buildFileWithImports(originalFile, imports)

    // FIXME: KTIJ-34274
    @OptIn(KaImplementationDetail::class)
    return KaBaseIllegalPsiException.allowIllegalPsiAccess { fileWithImports.importingScopeContext }.scopes.map { originalScope ->

        // we have to add this scope by hand, because buildFileWithImports builds a file without a package
        val fixedPackageScope = if (originalScope.kind is KaScopeKind.PackageMemberScope) {
            findPackageScope(originalFile)?.let { KaScopeWithKindImpl(it, originalScope.kind) }
        } else {
            null
        }

        fixedPackageScope ?: originalScope
    }
}

context(_: KaSession)
private fun findPackageScope(file: KtFile): KaScope? =
    findPackage(file.packageFqName)?.packageScope

/**
 * N.B. The resulting file DOES NOT have a package declaration.
 */
private fun buildFileWithImports(
    originalFile: KtFile,
    importsToGenerate: Collection<ImportPath>
): KtFile {
    val imports = buildString {
        for (importPath in importsToGenerate) {
            append("import ")
            append(importPath)
            append("\n")
        }
    }

    // TODO this code fragment misses a package declaration
    val fileWithImports  = KtBlockCodeFragment(originalFile.project, "Dummy_" + originalFile.name, "", imports, originalFile)

    return fileWithImports
}

context(_: KaSession)
private fun nonImportingScopesForPosition(element: KtElement): List<KaScope> {
    val scopeContext = element.containingKtFile.scopeContext(element)

    // we have to filter scopes created by implicit receivers (like companion objects, for example); see KT-70108
    val implicitReceiverScopeIndices = scopeContext.implicitReceivers.map { it.scopeIndexInTower }.toSet()

    val nonImportingScopes = scopeContext.scopes
        .asSequence()
        .filterNot { it.kind is KaScopeKind.ImportingScope }
        .filterNot { it.kind.indexInTower in implicitReceiverScopeIndices }
        .map { it.scope }
        .toList()

    return nonImportingScopes
}

context(_: KaSession)
internal fun typeIsPresentAsImplicitReceiver(
    type: KaType,
    contextPosition: KtElement,
): Boolean {
    val containingFile = contextPosition.containingKtFile
    val implicitReceivers = containingFile.scopeContext(contextPosition).implicitReceivers

    return implicitReceivers.any { it.type.semanticallyEquals(type) }
}

context(_: KaSession)
internal fun isAccessibleAsMemberCallableDeclaration(
    symbol: KaCallableSymbol,
    contextPosition: KtElement,
): Boolean {
    if (containingDeclarationPatched(symbol) !is KaClassLikeSymbol) return false

    if (symbol !is KaNamedSymbol) return false

    val nonImportingScopes = nonImportingScopesForPosition(contextPosition).asCompositeScope()

    return nonImportingScopes.callables(symbol.name).any { it == symbol }
}

context(_: KaSession)
internal fun isAccessibleAsMemberClassifier(symbol: KaSymbol, element: KtElement): Boolean {
    if (symbol !is KaClassLikeSymbol || containingDeclarationPatched(symbol) !is KaClassLikeSymbol) return false

    val name = symbol.name ?: return false

    val nonImportingScopes = nonImportingScopesForPosition(element).asCompositeScope()

    val foundClasses = nonImportingScopes.classifiers(name)
    val foundClass = foundClasses.firstOrNull()

    return symbol == foundClass
}
