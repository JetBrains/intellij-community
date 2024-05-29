// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.projectStructure.fwdDeclaration

import org.jetbrains.kotlin.idea.base.projectStructure.moduleInfo.NativeKlibLibraryInfo
import org.jetbrains.kotlin.library.KLIB_PROPERTY_INCLUDED_FORWARD_DECLARATIONS
import org.jetbrains.kotlin.name.FqName
import java.util.Properties

/**
 * Utility functions for grouping K/N forward declarations from a native manifest properties or a [NativeKlibLibraryInfo].
 * Grouped declaration [FqName]s share the same package and so the same [org.jetbrains.kotlin.name.NativeForwardDeclarationKind].
 */
object KotlinForwardDeclarationsFqNameExtractor {
    fun getGroupedFwdDeclarations(libraryInfo: NativeKlibLibraryInfo): Map<FqName, List<FqName>> {
        val fqNames = getFwdDeclarationFqNames(libraryInfo)
        return groupByPackage(fqNames)
    }

    fun getGroupedFwdDeclarations(properties: Properties): Map<FqName, List<FqName>> {
        val fqNames = getFwdDeclarationFqNames(properties)
        return groupByPackage(fqNames)
    }

    fun getPackageFqNames(libraryInfo: NativeKlibLibraryInfo): List<FqName> {
        return getGroupedFwdDeclarations(libraryInfo).keys.toList()
    }

    internal fun groupByPackage(declarations: List<FqName>): Map<FqName, List<FqName>> =
        declarations.groupBy(FqName::parent)

    private fun getFwdDeclarationFqNames(libraryInfo: NativeKlibLibraryInfo): List<FqName> {
        return getFwdDeclarationFqNames(libraryInfo.resolvedKotlinLibrary.manifestProperties)
    }

    private fun getFwdDeclarationFqNames(properties: Properties): List<FqName> {
        val fwdDeclarations = properties[KLIB_PROPERTY_INCLUDED_FORWARD_DECLARATIONS]?.toString().orEmpty()
            .split(" ")
            .filter { it.isNotEmpty() }

        return fwdDeclarations.map(::FqName)
    }
}
