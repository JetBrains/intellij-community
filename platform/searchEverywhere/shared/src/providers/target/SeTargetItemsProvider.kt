// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.ide.actions.GotoActionBase
import com.intellij.ide.actions.searcheverywhere.AbstractGotoSEContributor
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.PersistentSearchEverywhereContributorFilter
import com.intellij.ide.actions.searcheverywhere.SearchEverywherePreviewFetcher
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.util.PsiElementListCellRenderer.ItemMatchers
import com.intellij.ide.util.gotoByName.ChooseByNameInScopeItemProvider
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNameModelEx
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.ChooseByNameViewModel
import com.intellij.ide.util.gotoByName.ChooseByNameWeightedItemProvider
import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeIdMapper
import com.intellij.ide.util.scopeChooser.ScopeSeparator
import com.intellij.ide.vfs.rpcId
import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.scopes.SearchScopeData
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SePreviewInfoFactory
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeTargetItemPresentationBuilder
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilterImpl
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.suspendLazy
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.text.matching.MatchingMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

/**
 * A raw search result, with the matchers that decide which parts of its presentation the UI highlights.
 */
class SeTargetRawItem(val rawItem: Any, val rawWeight: Int?, val matchers: ItemMatchers?)

class SeTargetPresentableItem(val rawItem: Any,
                              private val matchers: ItemMatchers?,
                              private val weight: Int,
                              private val presentation: TargetPresentation,
                              val extendedInfo: SeExtendedInfo,
                              val isMultiSelectionSupported: Boolean,
                              val isExactMatch: Boolean): SeItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeTargetItemPresentationBuilder()
    .withTargetPresentation(presentation, matchers, extendedInfo, isMultiSelectionSupported)
    .build()
}

@ApiStatus.Internal
class SeTargetItemsProvider private constructor(
  private val project: Project,
  private val psiContext: SmartPsiElementPointer<PsiElement?>?,
  private val operationDisposable: Disposable?,
  private val label: String,
  private val gotoModelProvider: (Project, ScopeDescriptor?, Set<FileTypeRef>) -> (FilteringGotoByModel<*>),
  private val typeFilterProvider: (Project) -> List<PersistentSearchEverywhereContributorFilter<*>>,
  private val extendedInfoCalculator: SeExtendedInfoCalculator,
) : Disposable {
  //region Search

  @OptIn(ExperimentalCoroutinesApi::class)
  fun getItemsFlow(params: SeParams, presentationProvider: suspend (SeTargetRawItem) -> SeTargetPresentableItem): Flow<SeTargetPresentableItem> =
    getItemsFlow(params)
      .buffer(capacity = 64)                            // let the search run ahead
      .flatMapMerge(concurrency = 10) { rawItem ->
        flow {
          emit(presentationProvider(rawItem))
        }
      }

  fun getItemsFlow(params: SeParams): Flow<SeTargetRawItem> = channelFlow {
    // The counters live outside the read action, because `readAction` restarts the block after a write action.
    val attemptCount = AtomicInteger(0)
    val sentCount = AtomicInteger(0)
    val startedAtNano = System.nanoTime()
    val pattern = normalizeQuery(params.inputQuery)
    if (pattern.isBlank()) return@channelFlow

    val (scopeDescriptor, hiddenTypes) = SeEverywhereFilterImpl.isEverywhere(params.filter)?.let { isEverywhere ->
      scopes.getValue().byId[isEverywhere] to null
    } ?: run {
      val targetsFilter = SeTargetsFilter.from(params.filter)

      targetsFilter.selectedScopeId?.let {
        scopes.getValue().byId[it]
      } to targetsFilter.hiddenTypes
    }

    val scope = scopeDescriptor?.scope as? GlobalSearchScope ?: GlobalSearchScope.projectScope(project)

    persistHiddenTypes(hiddenTypes)

    val hiddenTypeRefs = hiddenTypes?.toSet()?.let { hiddenTypes ->
      FileSearchEverywhereContributor.getAllFileTypes().filter { hiddenTypes.contains(it.displayName) }
    }?.toSet() ?: emptySet()

    try {
      readAction {
        val attempt = attemptCount.incrementAndGet()
        if (attempt > 1) {
          LOG.debug {
            "$label: read action restarted, attempt=$attempt, ${sentCount.get()} items are re-sent"
          }
        }

        val model = gotoModelProvider(project, scopeDescriptor, hiddenTypeRefs)
        if (operationDisposable != null && model is Disposable) {
          Disposer.register(operationDisposable, model)
        }

        if (!DumbService.isDumbAware(model) && isDumb(project)) {
          LOG.debug {
            "$label: skipped, the model ${model::class.simpleName} is not dumb aware and the project is dumb"
          }
          return@readAction
        }

        val context = psiContext?.element
        val provider = ChooseByNameModelEx.getItemProvider(model, context)
        val isEverywhere = scope.isSearchInLibraries
        val viewModel = MyViewModel(project, model)
        val defaultMatchers = createDefaultMatchers(pattern, model)

        LOG.debug {
          "$label: search start, attempt=$attempt, pattern='$pattern', " +
          "provider=${provider::class.simpleName}, scope='${scope.displayName}', " +
          "isEverywhere=$isEverywhere, psiContext=${context != null}"
        }

        blockingContextToIndicator {
          val progressIndicator = ProgressManager.getGlobalProgressIndicator()
          val fromModelCount = AtomicInteger(0)

          when (provider) {
            is ChooseByNameInScopeItemProvider -> {
              val parameters = FindSymbolParameters.wrap(pattern, scope)
              provider.filterElementsWithWeights(viewModel, parameters, progressIndicator
              ) { item: FoundItemDescriptor<*> ->
                fromModelCount.incrementAndGet()
                processElement(progressIndicator, model, item.item, item.weight, defaultMatchers, sentCount)
              }
            }
            is ChooseByNameWeightedItemProvider -> {
              provider.filterElementsWithWeights(viewModel, pattern, isEverywhere, progressIndicator
              ) { item: FoundItemDescriptor<*> ->
                fromModelCount.incrementAndGet()
                processElement(progressIndicator, model, item.item, item.weight, defaultMatchers, sentCount)
              }
            }
            else -> {
              provider.filterElements(viewModel, pattern, isEverywhere, progressIndicator) { element: Any ->
                fromModelCount.incrementAndGet()
                processElement(progressIndicator, model, element, null, defaultMatchers, sentCount)
              }
            }
          }

          LOG.debug {
            "$label: attempt=$attempt done, elementsFromModel=${fromModelCount.get()}, sent=${sentCount.get()}"
          }
        }
      }
      logSearchOutcome("finished", pattern, sentCount, attemptCount, startedAtNano)
    }
    catch (e: Throwable) {
      // `ProcessCanceledException` is a `CancellationException`, so one check covers both.
      val outcome = if (e is CancellationException) "cancelled" else "failed with ${e::class.simpleName}"
      logSearchOutcome(outcome, pattern, sentCount, attemptCount, startedAtNano)
      throw e
    }
  }

  private fun ProducerScope<SeTargetRawItem>.processElement(
    indicator: ProgressIndicator,
    model: ChooseByNameModel,
    element: Any?,
    weight: Int?,
    defaultMatchers: ItemMatchers,
    sentCount: AtomicInteger,
  ): Boolean {
    if (indicator.isCanceled) {
      LOG.debug {
        "$label: stopped, the indicator is cancelled after ${sentCount.get()} items"
      }
      return false
    }
    if (element == null) {
      LOG.error("SeTargetItemsProvider($label): null returned from $model")
      return true
    }

    runBlockingCancellable {
      LOG.debug {
        "$label: emitting ${element.toString().split('\n').firstOrNull()}, weight=$weight"
      }
      send(SeTargetRawItem(element, weight, itemMatchers(defaultMatchers, model, element)))
    }
    sentCount.incrementAndGet()

    return true
  }

  /**
   * The matchers of the whole query, before any item narrows them.
   */
  private fun createDefaultMatchers(pattern: String, model: ChooseByNameModel): ItemMatchers {
    val namePattern = ChooseByNamePopup.getTransformedPattern(pattern, model)
    return ItemMatchers(NameUtil.buildMatcherWithFallback("*$pattern", "*$namePattern", MatchingMode.IGNORE_CASE), null)
  }

  private fun itemMatchers(defaultMatchers: ItemMatchers, model: ChooseByNameModel, element: Any): ItemMatchers =
    if (model is GotoFileModel && element is PsiFileSystemItem) {
      GotoFileModel.convertToFileItemMatchers(defaultMatchers, element, model)
    }
    else {
      defaultMatchers
    }

  private fun logSearchOutcome(
    outcome: String, pattern: String, sentCount: AtomicInteger, attemptCount: AtomicInteger, startedAtNano: Long,
  ) {
    LOG.debug {
      "$label: $outcome, pattern='$pattern', sent=${sentCount.get()}, attempts=${attemptCount.get()}, " +
      "durationMs=${(System.nanoTime() - startedAtNano) / 1_000_000}"
    }
  }

  //endregion

  //region Scopes

  private val scopes: SuspendLazyProperty<SeTargetScopes> = suspendLazy { createScopes() }

  /**
   * The scope list that the scope chooser shows, or null when the model has no scope to offer.
   *
   * A provider that exposes this must resolve the same scope ids back in [getItemsFlow]. Both sides
   * read one [SeTargetScopes], so the ids always agree.
   */
  suspend fun getSearchScopesInfo(): SearchScopesInfo? = scopes.getValue().info

  private suspend fun createScopes(): SeTargetScopes {
    val descriptors = readAction {
      collectScopesWithSeparators(AbstractGotoSEContributor.createScopes(project, psiContext))
    }
    if (descriptors.isEmpty()) return SeTargetScopes(null, SeScopeByIdMap(emptyMap(), null, null))

    val descriptorByScopeId = mutableMapOf<String, ScopeDescriptor>()

    val scopeDataList = descriptors.mapNotNull { descriptor ->
      val name = descriptor.displayName ?: return@mapNotNull null
      val scopeId = SeScopeById.generateScopeId(name)

      SearchScopeData.from(descriptor, scopeId)?.also {
        descriptorByScopeId[scopeId] = descriptor
      }
    }

    fun scopeIdOf(name: @Nls String): String? = scopeDataList.firstOrNull { it.name == name }?.scopeId

    val projectScopeName = GlobalSearchScope.projectScope(project).displayName
    val everywhereScopeName = GlobalSearchScope.everythingScope(project).displayName
    val projectScopeId = scopeIdOf(projectScopeName)
    val everywhereScopeId = scopeIdOf(everywhereScopeName)

    // The scope chooser can auto toggle to the everywhere scope only when both ids resolve and differ.
    // See SeScopeChooserActionProvider.canToggleEverywhere. A null id disables the auto toggle silently.
    if (projectScopeId == null || everywhereScopeId == null) {
      SeLog.warn("$label: the auto toggle is off, because a scope id is missing. " +
                 "project='$projectScopeName' -> $projectScopeId, everywhere='$everywhereScopeName' -> $everywhereScopeId. " +
                 "Known scopes: ${scopeDataList.joinToString { it.name }}")
    }
    else {
      SeLog.log(SeLog.SCOPE) {
        "$label: scopes=${scopeDataList.size}, project='$projectScopeName', everywhere='$everywhereScopeName'"
      }
    }

    return SeTargetScopes(
      info = SearchScopesInfo(scopeDataList, projectScopeId, projectScopeId, everywhereScopeId),
      byId = SeScopeByIdMap(descriptorByScopeId, everywhereScopeId = everywhereScopeId, projectScopeId = projectScopeId),
    )
  }

  /**
   * Keeps the scopes that the scope chooser can show, in the shape that
   * `ScopeChooserAction.collectScopesAndSeparators` produces.
   */
  private fun collectScopesWithSeparators(descriptors: List<ScopeDescriptor>): List<ScopeDescriptor> {
    val items = mutableListOf<ScopeDescriptor>()
    var pendingSeparator: ScopeSeparator? = null

    for (descriptor in descriptors) {
      if (descriptor is ScopeSeparator) {
        if (items.isNotEmpty()) pendingSeparator = descriptor
        continue
      }
      if (descriptor.scopeEquals(null) || descriptor.scope !is GlobalSearchScope) continue

      pendingSeparator?.let {
        items.add(it)
        pendingSeparator = null
      }
      items.add(descriptor)
    }
    return items
  }

  //endregion

  //region Type filters

  /**
   * The persistent type filters of the model, in the order that the filter actions appear.
   */
  private val typeFilters: SuspendLazyProperty<List<PersistentSearchEverywhereContributorFilter<*>>> = suspendLazy {
    typeFilterProvider(project)
  }

  /**
   * The type list that the filter action at [index] shows, or an empty list when there is no such filter.
   */
  suspend fun getTypeVisibilityStates(index: Int): List<SeTypeVisibilityStatePresentation> =
    typeFilters.getValue().getOrNull(index)?.let {
      typeVisibilityStates(it)
    } ?: emptyList()

  private fun <T> typeVisibilityStates(filter: PersistentSearchEverywhereContributorFilter<T>): List<SeTypeVisibilityStatePresentation> =
    filter.allElements.map { element ->
      SeTypeVisibilityStatePresentation(filter.getElementText(element), filter.getElementIcon(element)?.rpcId(), filter.isSelected(element))
    }

  /**
   * Writes [hiddenTypes] into every persistent type filter.
   */
  private suspend fun persistHiddenTypes(hiddenTypes: List<String>?) {
    val hidden = hiddenTypes?.toSet() ?: return
    typeFilters.getValue().forEach { filter ->
      persistHiddenTypes(filter, hidden)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun persistHiddenTypes(filter: PersistentSearchEverywhereContributorFilter<*>, hiddenTypes: Set<String>) {
    val typedFilter = filter as PersistentSearchEverywhereContributorFilter<Any?>
    typedFilter.allElements.forEach { element ->
      typedFilter.setSelected(element, !hiddenTypes.contains(typedFilter.getElementText(element)))
    }
  }

  //endregion

  //region Extended info

  suspend fun getExtendedInfo(item: SeTargetRawItem): SeExtendedInfo = extendedInfoCalculator.infoFor(item)

  //endregion

  //region Preview

  /**
   * The disposables that a preview fetch opens, released in [dispose].
   *
   * `SearchEverywherePreviewFetcher.findFirstChild` can open a file to build the usage. It hands the
   * disposable back through the handler, and the file must stay open until this provider goes away.
   */
  private val previewDisposables = ConcurrentLinkedQueue<Disposable>()

  /**
   * Builds the preview of [rawItem], or returns null when the item shows none.
   *
   * `SearchEverywherePreviewFetcher.findFirstChild` starts with
   * `PSIPresentationBgRendererWrapper.toPsi`, so a raw item goes in with no wrapper around it.
   */
  suspend fun getPreviewInfo(rawItem: Any): SePreviewInfo? =
    fetchPreviewInfo(rawItem, project) { previewDisposables.add(it) }

  override fun dispose() {
    previewDisposables.forEach { Disposer.dispose(it) }
    previewDisposables.clear()
  }

  //endregion

  companion object {
    private val LOG = logger<SeTargetItemsProvider>()
    const val COROUTINE_BASED_GOTO_KEY = "search.everywhere.coroutine.based.goto"

    /**
     * Builds the preview of [rawItem], or returns null when the item shows none.
     */
    suspend fun fetchPreviewInfo(rawItem: Any, project: Project, disposableHandler: (Disposable) -> Unit): SePreviewInfo? {
      val usageInfo = readAction {
        SearchEverywherePreviewFetcher.findFirstChild(rawItem, project, disposableHandler)
      }
      val virtualFile = usageInfo?.virtualFile ?: return null

      // The PSI element is null for a class file that is not decompiled, so hide every library file.
      if (AppMode.isRemoteDevHost() &&
          readAction {
            val fileIndex = ProjectFileIndex.getInstance(project)
            fileIndex.isInLibraryClasses(virtualFile) || fileIndex.isInLibrarySource(virtualFile)
          }) {
        return null
      }

      val (startOffset, endOffset) = readAction {
        SearchEverywherePreviewFetcher.readRangeFromUsageInfo(usageInfo)
      } ?: return null

      return SePreviewInfoFactory.create(virtualFile.rpcId(), listOf(startOffset to endOffset))
    }

    /**
     * Removes the trailing space from the query. A trailing space is not part of a name.
     *
     * Only the trailing space goes. A leading space and an internal space stay, because a Goto matcher
     * and a command with an argument both use them.
     */
    fun normalizeQuery(rawQuery: String): String = rawQuery.trimEnd()

    /**
     * Tells whether a target matches exactly what the user typed, so that the result list can keep it
     * above a partial sibling. See IJPL-248758.
     */
    fun isExactMatch(
      isExactMatchFromItem: Boolean,
      presentableText: String,
      inputQuery: String,
      isFile: Boolean,
      inputQueryHasNoExtension: Boolean,
      isDirectory: Boolean,
    ): Boolean =
      isExactMatchFromItem || // IJPL-133399, IJPL-251596
      !isDirectory && ((presentableText == inputQuery) || // IJPL-55665
                       (isFile && inputQueryHasNoExtension && presentableText.startsWith("$inputQuery."))) // IJPL-55732, IJPL-156298

    suspend fun create(
      project: Project,
      dataContext: DataContext,
      operationDisposable: Disposable?,
      label: String,
      gotoModelProvider: (Project, ScopeDescriptor?, Set<FileTypeRef>) -> (FilteringGotoByModel<*>),
      typeFilterProvider: (Project) -> List<PersistentSearchEverywhereContributorFilter<*>> = { emptyList() },
      extendedInfoCalculator: SeExtendedInfoCalculator = SePsiExtendedInfoCalculator(),
    ): SeTargetItemsProvider {
      val psiContext = readAction {
        GotoActionBase.getPsiContext(dataContext)?.let { context ->
          SmartPointerManager.getInstance(project).createSmartPsiElementPointer(context)
        }
      }

      return SeTargetItemsProvider(project, psiContext, operationDisposable, label, gotoModelProvider, typeFilterProvider,
                                   extendedInfoCalculator)
    }
  }
}

private class MyViewModel(private val myProject: Project, private val myModel: ChooseByNameModel) : ChooseByNameViewModel {
  override fun getProject(): Project = myProject

  override fun getModel(): ChooseByNameModel = myModel

  override fun isSearchInAnyPlace(): Boolean = myModel.useMiddleMatching()

  override fun transformPattern(pattern: String): String = ChooseByNamePopup.getTransformedPattern(pattern, myModel)

  override fun canShowListForEmptyPattern(): Boolean = false

  override fun getMaximumListSizeLimit(): Int = 0
}

/**
 * The scope list of one [SeTargetItemsProvider], with the map that resolves a scope id back.
 *
 * Both halves come from one `AbstractGotoSEContributor.createScopes` call, so a scope id that reaches
 * the UI always resolves in the search path.
 *
 * The selected scope is not persisted. `AbstractGotoSEContributor` keeps its own selection per
 * contributor class in project user data, and that map is file private there. So a new session starts
 * on the project scope.
 */
@ApiStatus.Internal
class SeTargetScopes(val info: SearchScopesInfo?, val byId: SeScopeById)

@ApiStatus.Internal
interface SeScopeById {
  operator fun get(isEverywhere: Boolean): ScopeDescriptor?
  operator fun get(scopeId: String): ScopeDescriptor?

  companion object {
    private const val SCOPE_ID_SEPARATOR: Char = '_'

    fun generateScopeId(displayName: @Nls String): String =
      "${UUID.randomUUID()}$SCOPE_ID_SEPARATOR${ScopeIdMapper.instance.getScopeSerializationId(displayName)}"

    fun extractSerializationId(scopeId: String): String = scopeId.substringAfter(SCOPE_ID_SEPARATOR)
  }
}

internal class SeScopeByIdMap(
  private val scopeIdToScope: Map<String, ScopeDescriptor>,
  private val everywhereScopeId: String?,
  private val projectScopeId: String?,
) : SeScopeById {
  override fun get(isEverywhere: Boolean): ScopeDescriptor? =
    (if (isEverywhere) everywhereScopeId else projectScopeId)?.let { scopeIdToScope[it] }

  override fun get(scopeId: String): ScopeDescriptor? = scopeIdToScope[scopeId]
}
