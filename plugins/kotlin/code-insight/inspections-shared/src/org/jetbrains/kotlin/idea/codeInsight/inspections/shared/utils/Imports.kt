// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared.utils

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * Checks if a package is already imported via star import.
 */
// TODO Use Import insertion API after KTIJ-28838 is fixed
internal fun isPackageImportedByStarImport(file: KtFile, packageFqName: FqName): Boolean {
    return file.importDirectives.any {
        it.importPath == ImportPath(packageFqName, isAllUnder = true)
    }
}
