// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import icons.GradleIcons
import org.jetbrains.plugins.gradle.service.resolve.findVersionCatalogEntriesMatching
import org.jetbrains.plugins.gradle.service.resolve.getVersionCatalogFiles

internal class KotlinGradleVersionCatalogCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val element = parameters.position
        val input = getInput(parameters)
        if (input.isBlank()) return
        val module = findModuleForPsiElement(element) ?: return
        val versionCatalogs = getVersionCatalogFiles(module)

        if (input.contains('.')) {
            // libs.juni<caret>
            val (catalogName, entryPath) = input.split('.', limit = 2)
            val virtualFile = versionCatalogs[catalogName] ?: return
            addLookupForCatalogEntries(result, catalogName, virtualFile, entryPath, element)
        } else {
            // lib<caret>, junit<caret>, versions.juni<caret>
            addLookupForCatalogNames(versionCatalogs, input, result)
            versionCatalogs.entries.forEach { (catalogName, catalogFile) ->
                addLookupForCatalogEntries(result, catalogName, catalogFile, input, element)
            }
        }
    }
}

internal fun getInput(parameters: CompletionParameters): String {
    val documentText = parameters.editor.document.text
    val offset = parameters.offset
    val startOffset = getDependencyCompletionStartOffset(documentText, offset)
    return documentText.substring(startOffset, offset)
}

private fun addLookupForCatalogNames(
    catalogNameToFile: Map<String, VirtualFile>,
    input: String,
    result: CompletionResultSet,
) {
    val catalogs = catalogNameToFile.filterKeys { catalogName ->
        catalogName.contains(input, ignoreCase = true)
    }
    val lookup = catalogs.map { (catalogName, _) ->
        LookupElementBuilder.create(catalogName)
            .withTypeText("Gradle version catalog")
            .withIcon(GradleIcons.GradleFile)
    }
    result.addAllElements(lookup)
}

private fun addLookupForCatalogEntries(
    result: CompletionResultSet,
    catalogName: String,
    catalogVirtualFile: VirtualFile,
    entryPath: String,
    element: PsiElement
) {
    val catalogPsiFile = element.manager.findFile(catalogVirtualFile) ?: return
    val entries = findVersionCatalogEntriesMatching(catalogPsiFile, entryPath)
    val lookup = entries.map {
        val fullReference = "$catalogName.${it.pathForBuildScript}"
        LookupElementBuilder.create(fullReference)
            .withIcon(GradleIcons.GradleFile)
            .withInsertHandler(DotQualifiedExpressionInsertHandler)
    }
    result.addAllElements(lookup)
}