// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.imports

import org.jetbrains.kotlin.idea.util.getSourceRoot
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath

/**
 * Utilities to mark specific non-common modules as "pseudo-common" from the import optimization perspective.
 *
 * In IntelliJ platform, we have multiple modules which are in fact JVM, but are also compiled as common ones.
 * In such modules, we don't want import optimizer to remove imports which are imported by default only on some platforms
 * (for example, annotations from `kotlin.jvm` package).
 *
 * Such modules can be marked as "pseudo-common" by adding a file named [PSEUDO_COMMON_SOURCE_SET_MARKER_FILE]
 * to the particular source root.
 *
 * N.B. This should only be used in `intellij` monorepo!
 * This functionality is non-specified, non-production-ready
 * and can be removed at any moment.
 *
 * See KTIJ-34200 for the details.
 */
internal object PseudoCommonSourceSetUtils {
    private const val PSEUDO_COMMON_SOURCE_SET_MARKER_FILE: String = ".pseudoCommonKotlinSourceSet"

    fun inPseudoCommonSourceSet(file: KtFile): Boolean {
        val sourceRoot = file.viewProvider.virtualFile.getSourceRoot(file.project) ?: return false

        return sourceRoot.findChild(PSEUDO_COMMON_SOURCE_SET_MARKER_FILE) != null
    }

    // Note: currently contains only jvm-specific default imports;
    // should be extended if needed.
    val PLATFORM_SPECIFIC_IMPORTS: Set<ImportPath> = setOf(
        ImportPath.fromString("kotlin.jvm.*")
    )
}