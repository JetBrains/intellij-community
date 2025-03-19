// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.psi.imports

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.core.formatter.KotlinPackageEntry
import org.jetbrains.kotlin.idea.core.formatter.KotlinPackageEntry.Companion.ALL_OTHER_ALIAS_IMPORTS_ENTRY
import org.jetbrains.kotlin.idea.core.formatter.KotlinPackageEntryTable
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath

@ApiStatus.Internal
class KotlinImportPathComparator private constructor(private val packageTable: KotlinPackageEntryTable) : Comparator<ImportPath> {

    /**
     * When `true`, import aliases should be sorted as a separate group of imports.
     *
     * When `false`, import aliases should be sorted together with all other imports.
     *
     * @see [org.jetbrains.kotlin.idea.formatter.KotlinImportOrderLayoutPanel.cbImportAliasesSeparately]
     */
    private val importAliasesSeparately: Boolean
        get() = ALL_OTHER_ALIAS_IMPORTS_ENTRY in packageTable.getEntries()

    override fun compare(import1: ImportPath, import2: ImportPath): Int {
        val ignoreAlias = if (importAliasesSeparately) {
            import1.hasAlias() && import2.hasAlias()
        } else {
            true
        }

        return compareValuesBy(
            import1,
            import2,
            { import -> bestEntryMatchIndex(import, ignoreAlias) },
            { import ->
                // Ignore backticks when comparing lexicographically
                import.toString().replace("`", "")
            }
        )
    }

    private fun bestEntryMatchIndex(path: ImportPath, ignoreAlias: Boolean): Int {
        var bestEntryMatch: KotlinPackageEntry? = null
        var bestIndex: Int = -1

        for ((index, entry) in packageTable.getEntries().withIndex()) {
            if (entry.isBetterMatchForPackageThan(bestEntryMatch, path, ignoreAlias)) {
                bestEntryMatch = entry
                bestIndex = index
            }
        }

        return bestIndex
    }

    companion object {
        fun create(file: KtFile): Comparator<ImportPath> {
            val packagesImportLayout = file.kotlinCustomSettings.PACKAGES_IMPORT_LAYOUT
            return KotlinImportPathComparator(packagesImportLayout)
        }
    }
}

private fun KotlinPackageEntry.isBetterMatchForPackageThan(entry: KotlinPackageEntry?, path: ImportPath, ignoreAlias: Boolean): Boolean {
    if (!matchesImportPath(path, ignoreAlias)) return false
    if (entry == null) return true

    // Any matched package is better than ALL_OTHER_IMPORTS_ENTRY
    if (this == KotlinPackageEntry.ALL_OTHER_IMPORTS_ENTRY) return false
    if (entry == KotlinPackageEntry.ALL_OTHER_IMPORTS_ENTRY) return true

    if (entry.withSubpackages != withSubpackages) return !withSubpackages

    return entry.packageName.count { it == '.' } < packageName.count { it == '.' }
}

private fun KotlinPackageEntry.matchesImportPath(importPath: ImportPath, ignoreAlias: Boolean): Boolean {
    if (!ignoreAlias && importPath.hasAlias()) {
        return this == ALL_OTHER_ALIAS_IMPORTS_ENTRY
    }

    if (this == KotlinPackageEntry.ALL_OTHER_IMPORTS_ENTRY) return true

    return matchesPackageName(importPath.pathStr)
}
