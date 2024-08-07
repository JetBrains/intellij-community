// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.imports

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaScopeContext
import org.jetbrains.kotlin.psi.KtBlockCodeFragment
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

