// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeContext
import org.jetbrains.kotlin.analysis.api.components.KaScopeKind
import org.jetbrains.kotlin.analysis.api.scopes.KaScope
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath

internal fun KaSession.buildScopeContextByImports(originalFile: KtFile, importsToGenerate: Collection<ImportPath>): KaScopeContext {
    val fileWithImports = buildFileWithImports(originalFile, importsToGenerate)

    return fileWithImports.importingScopeContext
}

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

internal fun KaSession.nonImportingScopesForPosition(element: KtElement): List<KaScope> {
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