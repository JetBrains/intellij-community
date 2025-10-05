// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.imports

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.imports.KaDefaultImport
import org.jetbrains.kotlin.analysis.api.imports.KaDefaultImportPriority
import org.jetbrains.kotlin.analysis.api.imports.KaDefaultImports
import org.jetbrains.kotlin.analysis.api.imports.KaDefaultImportsProvider
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.scripting.definitions.ScriptConfigurationsProvider

@Service
@ApiStatus.Experimental
class KotlinIdeDefaultImportProvider {

    fun isImportedWithDefault(importPath: ImportPath, contextFile: KtFile): Boolean {
        val (defaultImports, excludedImports) = computeDefaultAndExcludedImports(contextFile)

        return importPath.isImported(defaultImports, excludedImports)
    }

    fun isImportedWithLowPriorityDefaultImport(importPath: ImportPath, contextFile: KtFile): Boolean {
        val defaultImports = KaDefaultImportsProvider.getService(contextFile.project).getDefaultImports(contextFile.platform)

        val defaultLowPriorityImports = defaultImports.defaultLowPriorityImports.map { it.importPath }
        val excludedImports = defaultImports.excludedFromDefaultImports.map { it.fqName }

        return importPath.isImported(defaultLowPriorityImports, excludedImports)
    }

    fun computeDefaultAndExcludedImports(contextFile: KtFile): Pair<List<ImportPath>, List<FqName>> {
        val defaultImports = KaDefaultImportsProvider.getService(contextFile.project).getDefaultImports(contextFile.platform)

        val allDefaultImports = defaultImports.defaultImports.map { it.importPath }.toMutableList()
        val excludedImports = defaultImports.excludedFromDefaultImports.map { it.fqName }

        if (PseudoCommonSourceSetUtils.inPseudoCommonSourceSet(contextFile)) {
            allDefaultImports.removeAll(PseudoCommonSourceSetUtils.PLATFORM_SPECIFIC_IMPORTS)
        }

        val scriptExtraImports = contextFile.takeIf { it.isScript() }?.let { ktFile ->
            val scriptDependencies = ScriptConfigurationsProvider.getInstance(ktFile.project)
                ?.getScriptConfiguration(ktFile.originalFile as KtFile)

            scriptDependencies?.defaultImports?.map { ImportPath.fromString(it) }
        }.orEmpty()

        return (allDefaultImports + scriptExtraImports) to excludedImports
    }

    companion object {
        @JvmStatic
        fun getInstance(): KotlinIdeDefaultImportProvider = service()
    }
}

private val KaDefaultImports.defaultLowPriorityImports: List<KaDefaultImport>
    get() = defaultImports.filter { it.priority == KaDefaultImportPriority.LOW }

//region Copy of `isImported` functions from `fqNameUtils.kt`

private fun FqName.isImported(importPath: ImportPath, skipAliasedImports: Boolean = true): Boolean {
    return when {
        skipAliasedImports && importPath.hasAlias() -> false
        importPath.isAllUnder && !isRoot -> importPath.fqName == this.parent()
        else -> importPath.fqName == this
    }
}

private fun ImportPath.isImported(alreadyImported: ImportPath): Boolean {
    return if (isAllUnder || hasAlias()) this == alreadyImported else fqName.isImported(alreadyImported)
}

private fun ImportPath.isImported(imports: Iterable<ImportPath>): Boolean = imports.any { isImported(it) }

private fun ImportPath.isImported(imports: Iterable<ImportPath>, excludedFqNames: Iterable<FqName>): Boolean {
    return isImported(imports) && (isAllUnder || this.fqName !in excludedFqNames)
}

//endregion

