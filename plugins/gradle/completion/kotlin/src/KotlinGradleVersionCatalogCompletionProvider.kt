// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.kotlin

import com.intellij.codeInsight.completion.BaseCompletionLookupArranger
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementWeigher
import com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import icons.GradleIcons
import org.jetbrains.plugins.gradle.service.resolve.VersionCatalogSection
import org.jetbrains.plugins.gradle.service.resolve.findVersionCatalogEntriesFromSections
import org.jetbrains.plugins.gradle.service.resolve.getVersionCatalogFiles
import org.jetbrains.plugins.gradle.util.useDependencyCompletionService

internal class KotlinGradleVersionCatalogCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    if (!useDependencyCompletionService()) return

    val element = parameters.position
    val input = getInput(parameters)

    when {
      insideScriptBlockPattern(PLUGINS).accepts(element) ->
        // plugins { <caret> }
        doAddCompletions(result, input, element, defaultSectionsFilter = setOf(VersionCatalogSection.PLUGINS))

      insideScriptBlockPattern(DEPENDENCIES).accepts(element) -> {
        if (element.isDependencyArgumentWithoutQuotes()) {
          // dependencies { implementation(<caret>) }
          // dependencies { implementation(platform(<caret>)) }
          val sortedResult = result.withRelevanceSorter(
            CompletionSorter.defaultSorter(parameters, result.prefixMatcher)
              .weighBefore("middleMatching", GradleVersionCatalogWeigher)
          )
          val defaultSections = setOf(VersionCatalogSection.LIBRARIES, VersionCatalogSection.BUNDLES)
          doAddCompletions(sortedResult, input, element, defaultSectionsFilter = defaultSections)
        }
      }

      else ->
        doAddCompletions(result, input, element)
    }
  }

  private fun doAddCompletions(
    result: CompletionResultSet,
    input: String,
    element: PsiElement,
    defaultSectionsFilter: Set<VersionCatalogSection> = emptySet(),
  ) {
    val module = findModuleForPsiElement(element) ?: return
    val versionCatalogs = getVersionCatalogFiles(module)
    if (input.contains('.')) {
      // libs.juni<caret>
      val inputParts = input.split('.')
      val catalogName = inputParts.first()

      val section = inputParts.getOrNull(1)?.let { VersionCatalogSection.fromStringOrNull(it) }
      val sectionsFilter = if (section != null) setOf(section) else defaultSectionsFilter

      val virtualFile = versionCatalogs[catalogName] ?: return
      val catalogPsiFile = element.manager.findFile(virtualFile) ?: return

      val unmodifiableInput = input.substringBeforeLastOrNull(".")
      addLookupForCatalogEntries(result, catalogName, catalogPsiFile, sectionsFilter, unmodifiableInput)
    }
    else {
      // lib<caret>, junit<caret>, versions.juni<caret>
      addLookupForCatalogNames(versionCatalogs, input, result)
      versionCatalogs.entries.forEach { (catalogName, catalogFile) ->
        val catalogPsiFile = element.manager.findFile(catalogFile) ?: return@forEach
        addLookupForCatalogEntries(result, catalogName, catalogPsiFile, defaultSectionsFilter)
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
    val catalogNameLookup = LookupElementBuilder.create(catalogName)
      .withTypeText("Gradle version catalog")
      .withIcon(GradleIcons.GradleFile)
      .also {
        it.putUserData(BaseCompletionLookupArranger.FORCE_MIDDLE_MATCH, Any())
        it.putUserData(GRADLE_LOOKUP_TYPE_KEY, GradleLookupType.VERSION_CATALOG_NAME)
      }
    MLRankingIgnorable.wrap(catalogNameLookup)
  }
  result.addAllElements(lookup)
}

private fun addLookupForCatalogEntries(
  result: CompletionResultSet,
  catalogName: String,
  catalogPsiFile: PsiFile,
  sectionsFilter: Set<VersionCatalogSection>,
  unmodifiableInput: String? = null,
) {
  val entries = findVersionCatalogEntriesFromSections(catalogPsiFile, sectionsFilter)
  val lookup = entries.map { catalogEntry ->
    val fullReference = "$catalogName.${catalogEntry.pathForBuildScript}"
    val toReplaceInput = when (unmodifiableInput) {
      null -> fullReference
      else -> fullReference.substringAfter("$unmodifiableInput.")
    }
    LookupElementBuilder.create(toReplaceInput)
      .withIcon(GradleIcons.GradleFile)
      .withTypeText("Entry in `$catalogName` version catalog")
      .withInsertHandler(GradleVersionCatalogExpressionInsertHandler)
      .also {
        it.putUserData(BaseCompletionLookupArranger.FORCE_MIDDLE_MATCH, Any())
        it.putUserData(GRADLE_LOOKUP_TYPE_KEY, GradleLookupType.VERSION_CATALOG_ALIAS)
      }
  }
  result.addAllElements(lookup)
}

private fun String.substringBeforeLastOrNull(delimiter: String): String? {
  val index = lastIndexOf(delimiter)
  return if (index == -1) null else substring(0, index)
}

private object GradleVersionCatalogWeigher : LookupElementWeigher("gradle.completion.versionCatalog.weigher") {
  override fun weigh(element: LookupElement): Comparable<*> {
    val completionType = element.getUserData(GRADLE_LOOKUP_TYPE_KEY)
    return when (completionType) {
      GradleLookupType.VERSION_CATALOG_NAME -> 0
      GradleLookupType.VERSION_CATALOG_ALIAS -> 1
      else -> Int.MAX_VALUE
    }
  }
}

private enum class GradleLookupType {
  VERSION_CATALOG_NAME,
  VERSION_CATALOG_ALIAS,
}

private val GRADLE_LOOKUP_TYPE_KEY = Key.create<GradleLookupType>("gradle.completion.lookup.type")
