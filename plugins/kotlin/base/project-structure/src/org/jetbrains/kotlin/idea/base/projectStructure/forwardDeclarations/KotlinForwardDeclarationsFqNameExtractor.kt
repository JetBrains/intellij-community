// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure.forwardDeclarations

import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.NativeKlibLibraryInfo
import org.jetbrains.kotlin.library.KLIB_PROPERTY_INCLUDED_FORWARD_DECLARATIONS
import org.jetbrains.kotlin.name.FqName
import java.util.Properties

/**
 * Utility functions for grouping K/N forward declarations from a native manifest properties or a [NativeKlibLibraryInfo].
 * Grouped declaration [FqName]s share the same package and so the same [org.jetbrains.kotlin.name.NativeForwardDeclarationKind].
 */
internal object KotlinForwardDeclarationsFqNameExtractor {
    fun getGroupedForwardDeclarations(libraryInfo: NativeKlibLibraryInfo): Map<FqName, List<FqName>> {
        val fqNames = getForwardDeclarationFqNames(libraryInfo)
        return groupByPackage(fqNames)
    }

    fun getGroupedForwardDeclarations(properties: Properties): Map<FqName, List<FqName>> {
        val fqNames = getForwardDeclarationFqNames(properties)
        return groupByPackage(fqNames)
    }

    fun getPackageFqNames(libraryInfo: NativeKlibLibraryInfo): List<FqName> {
        return getGroupedForwardDeclarations(libraryInfo).keys.toList()
    }

    internal fun groupByPackage(declarations: List<FqName>): Map<FqName, List<FqName>> =
        declarations.filterNot { it.isRoot }.groupBy(FqName::parent)

    private fun getForwardDeclarationFqNames(libraryInfo: NativeKlibLibraryInfo): List<FqName> {
        return getForwardDeclarationFqNames(libraryInfo.resolvedKotlinLibrary.manifestProperties)
    }

    private fun getForwardDeclarationFqNames(properties: Properties): List<FqName> {
        val forwardDeclarations = properties[KLIB_PROPERTY_INCLUDED_FORWARD_DECLARATIONS]?.toString().orEmpty()
            .split(" ")
            .filter { it.isNotEmpty() }

        return forwardDeclarations.map(::FqName)
    }
}
