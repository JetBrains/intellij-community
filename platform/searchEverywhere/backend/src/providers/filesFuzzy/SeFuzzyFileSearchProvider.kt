// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.filesFuzzy

import com.intellij.ide.actions.GotoActionBase
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilter
import com.intellij.platform.searchEverywhere.providers.SeScopeById
import com.intellij.platform.searchEverywhere.providers.SeScopeByIdFiles
import com.intellij.platform.searchEverywhere.providers.target.SeTargetsFilter
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.suspendLazy
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Processor
import com.intellij.util.fuzzyMatching.FuzzyMatchResult
import com.intellij.util.indexing.IdFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * SeItemsProvider implementation for fuzzy file search using Smith-Waterman algorithm.
 *
 * This provider searches through all project files and scores them using the Smith-Waterman
 * local sequence alignment algorithm, which provides FZF-style fuzzy matching with
 * position-based bonuses.
 *
 * Features:
 * - Position-aware fuzzy matching (first char, consecutive, camelCase, separator bonuses)
 * - Configurable minimum score threshold via registry
 * - Configurable maximum results limit via registry
 * - Back pressure support via SeItemsProvider.Collector
 *
 * Registry keys:
 * - fuzzySearch.enabled: Enable/disable the provider (boolean)
 * - fuzzySearch.minScore: Minimum match score threshold (int, default 50)
 */
@ApiStatus.Internal
class SeFuzzyFileSearchProvider(
  private val project: Project,
  dataContext: DataContext,
) : SeItemsProvider {

  override val id: String = SeProviderIdUtils.FUZZY_FILES_ID
  override val displayName: String = "Files (fuzzy)"

  private val scopeById: SuspendLazyProperty<SeScopeById> = suspendLazy {
    val psiContext = readAction {
      GotoActionBase.getPsiContext(dataContext)?.let { context ->
        SmartPointerManager.getInstance(project).createSmartPsiElementPointer(context)
      }
    }

    SeScopeByIdFiles(project, psiContext)
  }

  override suspend fun collectItems(
    params: SeParams,
    collector: SeItemsProvider.Collector,
  ) : Unit = coroutineScope {
    val query = params.inputQuery.trim()
    if (query.isBlank()) return@coroutineScope

    val (scopeDescriptor, hiddenTypes) = SeEverywhereFilter.isEverywhere(params.filter)?.let { isEverywhere ->
      scopeById.getValue()[isEverywhere] to null
    } ?: run {
      val targetsFilter = SeTargetsFilter.from(params.filter)

      targetsFilter.selectedScopeId?.let {
        scopeById.getValue()[it]
      } to targetsFilter.hiddenTypes
    }

    val searchScope = scopeDescriptor?.scope as? GlobalSearchScope ?: GlobalSearchScope.projectScope(project)
    val isEverywhere = searchScope.isSearchInLibraries

    val hiddenTypeRefs = hiddenTypes?.toSet()?.let { hiddenTypes ->
      FileSearchEverywhereContributor.getAllFileTypes().filter { hiddenTypes.contains(it.displayName) }
    }?.toSet() ?: emptySet()

    val namesProcessor = NamesProcessor(this, searchScope, hiddenTypeRefs, query, project, collector)

    ReadAction.nonBlocking<Boolean> {
      FilenameIndex.processAllFileNames(namesProcessor, searchScope, IdFilter.getProjectIdFilter(project, isEverywhere))
      true
    }.executeSynchronously()

    namesProcessor.close()
  }

  override suspend fun itemSelected(
    item: SeItem,
    modifiers: Int,
    searchText: String,
  ): Boolean {
    if (item !is SeFuzzyFileSearchItem) return false
    val navigatable = item.rawObject.item as? Navigatable

    withContext(Dispatchers.EDT) {
      navigatable?.navigate(true)
    }

    return true
  }

  override suspend fun canBeShownInFindResults(): Boolean = true

  override fun dispose() {
    // Cleanup if needed
  }

  private class NamesProcessor(
    val coroutineScope: CoroutineScope,
    val searchScope: GlobalSearchScope,
    val hiddenTypes: Set<FileTypeRef>,
    query: String,
    val project: Project,
    val collector: SeItemsProvider.Collector,
  ) : Processor<String> {
    private val channel = Channel<Pair<String, FuzzyMatchResult>>(capacity = 1000)
    val matcher = SmithWatermanMatcher(query)
    val minScore = Registry.intValue("search.everywhere.fuzzy.files.min.score", 6500)

    init {
      val psiManager = PsiManager.getInstance(project)

      coroutineScope.launch {
        for ((name, matchingResult) in channel) {
          val files = readAction {
            FilenameIndex.getVirtualFilesByName(name, searchScope).mapNotNull { file ->
              if (FileTypeRef.forFileType(file.fileType) in hiddenTypes) return@mapNotNull null

              psiManager.findFile(file)?.let { psiFile ->
                PSIPresentationBgRendererWrapper.PsiItemWithPresentation(
                  psiFile,
                  TargetPresentation.builder(psiFile.name).icon(psiFile.getIcon(0)).locationText(file.parent?.path).presentation()
                ) to file
              }
            }
          }
          files.forEach { (itemWithPresentation, file) ->
            val normalizedScore =
              if (file.isDirectory) matchingResult.normalizedScore / 2
              else matchingResult.normalizedScore

            val item = SeFuzzyFileSearchItem(itemWithPresentation, file, project, normalizedScore, matchingResult.matchedIndices)
            collector.put(item)
          }
        }
      }
    }

    fun close() {
      channel.close()
    }

    override fun process(name: String): Boolean {
      coroutineScope.ensureActive()
      val result = matcher.match(name)

      if (result.normalizedScore * SeFuzzyFileSearchItem.MAX_FUZZY_WEIGHT < minScore) return true

      // trySend returns failure when channel is full (1000 items buffered) — stop processing
      return channel.trySend(name to result).isSuccess
    }
  }
}
