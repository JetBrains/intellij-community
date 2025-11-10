// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.imports

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * Checks if a package is already imported via star import.
 */
private fun isPackageImportedByStarImport(file: KtFile, packageFqName: FqName): Boolean {
    return file.importDirectives.any {
        it.importPath == ImportPath(packageFqName, isAllUnder = true)
    }
}

// TODO Use Import insertion API after KTIJ-28838 is fixed
@ApiStatus.Internal
fun KtFile.addImportFor(fqName: FqName) {
    val importPath = ImportPath(fqName, isAllUnder = false)

    val defaultImportProvider = KotlinIdeDefaultImportProvider.getInstance()
    if (
        defaultImportProvider.isImportedWithDefault(importPath, this) ||
        defaultImportProvider.isImportedWithLowPriorityDefaultImport(importPath, this)
    ) return

    if (isPackageImportedByStarImport(this, fqName.parent())) return

    addImport(fqName)
}