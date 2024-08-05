// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.imports

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.scripting.definitions.ScriptDependenciesProvider

@Service
@ApiStatus.Experimental
class KotlinIdeDefaultImportProvider {

    fun isImportedWithDefault(importPath: ImportPath, contextFile: KtFile): Boolean {
        val (defaultImports, excludedImports) = computeDefaultAndExcludedImports(contextFile)

        return importPath.isImported(defaultImports, excludedImports)
    }

    fun isImportedWithLowPriorityDefaultImport(importPath: ImportPath, contextFile: KtFile): Boolean {
        val analyzerServices = contextFile.platform.findAnalyzerServices(contextFile.project)

        return importPath.isImported(analyzerServices.defaultLowPriorityImports, analyzerServices.excludedImports)
    }

    fun computeDefaultAndExcludedImports(contextFile: KtFile): Pair<List<ImportPath>, List<FqName>> {
        val languageVersionSettings = contextFile.languageVersionSettings
        val analyzerServices = contextFile.platform.findAnalyzerServices(contextFile.project)
        val allDefaultImports = analyzerServices.getDefaultImports(languageVersionSettings, includeLowPriorityImports = true)

        val scriptExtraImports = contextFile.takeIf { it.isScript() }?.let { ktFile ->
            val scriptDependencies = ScriptDependenciesProvider.getInstance(ktFile.project)
                ?.getScriptConfiguration(ktFile.originalFile as KtFile)
            scriptDependencies?.defaultImports?.map { ImportPath.fromString(it) }
            scriptDependencies?.defaultImports?.map { ImportPath.fromString(it) }
        }.orEmpty()

        return (allDefaultImports + scriptExtraImports) to analyzerServices.excludedImports
    }

    companion object {
        @JvmStatic
        fun getInstance(): KotlinIdeDefaultImportProvider = service()
    }
}

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

