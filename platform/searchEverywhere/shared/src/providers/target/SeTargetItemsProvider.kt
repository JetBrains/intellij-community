// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.ide.actions.GotoActionBase
import com.intellij.ide.actions.searcheverywhere.AbstractGotoSEContributor
import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.PersistentSearchEverywhereContributorFilter
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributorWrapper
import com.intellij.ide.actions.searcheverywhere.SearchEverywherePreviewFetcher
import com.intellij.ide.actions.searcheverywhere.SemanticSearchEverywhereContributor
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.util.PsiElementListCellRenderer.ItemMatchers
import com.intellij.ide.util.gotoByName.ChooseByNameInScopeItemProvider
import com.intellij.ide.util.gotoByName.ChooseByNameMatcherFactory
import com.intellij.ide.util.gotoByName.ChooseByNameModel
import com.intellij.ide.util.gotoByName.ChooseByNameModelEx
import com.intellij.ide.util.gotoByName.ChooseByNamePopup
import com.intellij.ide.util.gotoByName.ChooseByNameViewModel
import com.intellij.ide.util.gotoByName.ChooseByNameWeightedItemProvider
import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.FilteringGotoByModel
import com.intellij.ide.util.gotoByName.GotoFileModel
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.util.scopeChooser.ScopeIdMapper
import com.intellij.ide.util.scopeChooser.ScopeSeparator
import com.intellij.ide.vfs.rpcId
import com.intellij.idea.AppMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.scopes.SearchScopeData
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SePreviewInfoFactory
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeTargetItemPresentationBuilder
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilterImpl
import com.intellij.platform.searchEverywhere.providers.SeLog
import com.intellij.platform.searchEverywhere.providers.target.presentation.SeTargetPresentationProvider
import com.intellij.platform.searchEverywhere.providers.target.selection.SeTargetItemSelectionProcessor
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.suspendLazy
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.codeStyle.MatcherWithFallback
import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.FindSymbolParameters
import com.intellij.util.text.matching.MatchingMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.takeWhile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.InputEvent
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

/**
 * A raw search result, with the matchers that decide which parts of its presentation the UI highlights.
 */
@ApiStatus.Experimental
class SeTargetRawItem(val rawItem: Any, val rawWeight: Int?, val matchers: ItemMatchers?)

@ApiStatus.Experimental
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

@ApiStatus.Experimental
class SeTargetItemsProvider<T> private constructor(
  private val project: Project,
  private val psiContext: SmartPsiElementPointer<PsiElement?>?,
  private val operationDisposable: Disposable?,
  private val label: String,
  private val gotoModelProvider: (Project, ScopeDescriptor?, Set<T>) -> (FilteringGotoByModel<*>),
  private val typeFilterProvider: (Project) -> List<PersistentSearchEverywhereContributorFilter<T>>,
  private val extendedInfoCalculator: SeExtendedInfoCalculator,
  private val isFileProvider: Boolean,
) : Disposable {
  //region Search

  /**
   * Runs the search of [params] and sends every result to [collector].
   */
  suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector): Unit = coroutineScope {
    val inputQuery = normalizeQuery(params.inputQuery)
    val inputQueryHasNoExtension = !inputQuery.contains('.')

    getItemsFlow(params, presentationProvider = { fetchPresentation(it, inputQuery, inputQueryHasNoExtension) })
      .buffer(capacity = 0, onBufferOverflow = BufferOverflow.SUSPEND)
      .takeWhile {
        collector.put(it)
      }
      .collect()
  }

  private suspend fun fetchPresentation(
    item: SeTargetRawItem,
    inputQuery: String,
    inputQueryHasNoExtension: Boolean,
  ): SeTargetPresentableItem {
    val weight = item.rawWeight ?: 0
    val presentation = SeTargetPresentationProvider.computePresentation(item.rawItem)
                       ?: TargetPresentation.builder("").presentation()

    return SeTargetPresentableItem(
      rawItem = item.rawItem,
      matchers = item.matchers,
      weight = weight,
      presentation = presentation,
      extendedInfo = getExtendedInfo(item),
      isMultiSelectionSupported = true, // AbstractGotoSEContributor supports it for every goto model
      isExactMatch = isExactMatch(
        // The legacy verdict of the item. SeAsyncContributorWrapper derives it the same way.
        isExactMatchFromItem = DefaultChooseByNameItemProvider.isInExactMatchDegreeRange(weight),
        presentableText = presentation.presentableText,
        inputQuery = inputQuery,
        isFile = isFileProvider,
        inputQueryHasNoExtension = inputQueryHasNoExtension,
        isDirectory = PSIPresentationBgRendererWrapper.toPsi(item.rawItem) is PsiDirectory,
      ),
    )
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun getItemsFlow(params: SeParams, presentationProvider: suspend (SeTargetRawItem) -> SeTargetPresentableItem): Flow<SeTargetPresentableItem> {
    val stats = SeFetchStats(LOG.isDebugEnabled)
    val presentationNanos = AtomicLong(0)
    val presentedCount = AtomicInteger(0)
    val startedAtNano = System.nanoTime()

    return getItemsFlow(params)
      .buffer(capacity = RAW_ITEMS_BUFFER)              // let the search run ahead
      .flatMapMerge(concurrency = PRESENTATION_CONCURRENCY) { rawItem ->
        flow {
          val item = stats.measure(presentationNanos) { presentationProvider(rawItem) }
          presentedCount.incrementAndGet()
          emit(item)
        }
      }
      .onCompletion {
        LOG.debug {
          // The summed time counts every one of the parallel coroutines, so it can pass the wall time.
          "$label: presentation done, items=${presentedCount.get()}, " +
          "summedMs=${presentationNanos.get() / 1_000_000}, wallMs=${(System.nanoTime() - startedAtNano) / 1_000_000}, " +
          "concurrency=$PRESENTATION_CONCURRENCY, buffer=$RAW_ITEMS_BUFFER"
        }
      }
  }

  fun getItemsFlow(params: SeParams): Flow<SeTargetRawItem> = channelFlow {
    // The counters live outside the read action, because `readAction` restarts the block after a write action.
    val attemptCount = AtomicInteger(0)
    val stats = SeFetchStats(LOG.isDebugEnabled)
    val sentCount = stats.sentCount
    val startedAtNano = System.nanoTime()
    val pattern = normalizeQuery(params.inputQuery)
    if (pattern.isBlank()) return@channelFlow

    // The first search of a session builds the scope list here, so this can dominate its latency.
    val (scopeDescriptor, hiddenTypes) = stats.measure(stats.scopeResolveNanos) {
      SeEverywhereFilterImpl.isEverywhere(params.filter)?.let { isEverywhere ->
        scopes.getValue().byId[isEverywhere] to null
      } ?: run {
        val targetsFilter = SeTargetsFilter.from(params.filter)

        targetsFilter.selectedScopeId?.let {
          scopes.getValue().byId[it]
        } to targetsFilter.hiddenTypes
      }
    }

    val scope = scopeDescriptor?.scope as? GlobalSearchScope ?: GlobalSearchScope.projectScope(project)

    persistHiddenTypes(hiddenTypes)

    // The filters of the model know their own elements, so this stays free of any one model.
    val hiddenTypeRefs = hiddenTypes?.toSet()?.let { hiddenNames ->
      typeFilters.getValue().flatMap { filter ->
        filter.allElements.filter { hiddenNames.contains(filter.getElementText(it)) }
      }
    }?.toSet() ?: emptySet()

    try {
      readAction {
        val attempt = attemptCount.incrementAndGet()
        if (attempt > 1) {
          LOG.debug {
            "$label: read action restarted, attempt=$attempt, ${sentCount.get()} items are re-sent"
          }
        }

        val model = stats.measure(stats.modelCreateNanos) { gotoModelProvider(project, scopeDescriptor, hiddenTypeRefs) }
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
          val fromModelCount = stats.fromModelCount

          stats.measure(stats.modelSearchNanos) {
            when (provider) {
              is ChooseByNameInScopeItemProvider -> {
                val parameters = FindSymbolParameters.wrap(pattern, scope)
                provider.filterElementsWithWeights(viewModel, parameters, progressIndicator
                ) { item: FoundItemDescriptor<*> ->
                  fromModelCount.incrementAndGet()
                  processElement(progressIndicator, model, item.item, item.weight, defaultMatchers, stats, startedAtNano)
                }
              }
              is ChooseByNameWeightedItemProvider -> {
                provider.filterElementsWithWeights(viewModel, pattern, isEverywhere, progressIndicator
                ) { item: FoundItemDescriptor<*> ->
                  fromModelCount.incrementAndGet()
                  processElement(progressIndicator, model, item.item, item.weight, defaultMatchers, stats, startedAtNano)
                }
              }
              else -> {
                provider.filterElements(viewModel, pattern, isEverywhere, progressIndicator) { element: Any ->
                  fromModelCount.incrementAndGet()
                  processElement(progressIndicator, model, element, null, defaultMatchers, stats, startedAtNano)
                }
              }
            }
          }

          LOG.debug {
            "$label: attempt=$attempt done, ${stats.toLogString()}"
          }
        }
      }
      logSearchOutcome("finished", pattern, stats, attemptCount, startedAtNano)
    }
    catch (e: Throwable) {
      // `ProcessCanceledException` is a `CancellationException`, so one check covers both.
      val outcome = if (e is CancellationException) "cancelled" else "failed with ${e::class.simpleName}"
      logSearchOutcome(outcome, pattern, stats, attemptCount, startedAtNano)
      throw e
    }
  }

  private fun ProducerScope<SeTargetRawItem>.processElement(
    indicator: ProgressIndicator,
    model: ChooseByNameModel,
    element: Any?,
    weight: Int?,
    defaultMatchers: ItemMatchers,
    stats: SeFetchStats,
    startedAtNano: Long,
  ): Boolean {
    if (indicator.isCanceled) {
      LOG.debug {
        "$label: stopped, the indicator is cancelled after ${stats.sentCount.get()} items"
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
      // `send` waits for the consumer while the read lock is held, so the wait goes into the log.
      stats.measure(stats.blockedInSendNanos) {
        send(SeTargetRawItem(element, weight, itemMatchers(defaultMatchers, model, element)))
      }
    }
    stats.sentCount.incrementAndGet()
    stats.recordFirstItem(startedAtNano)

    return true
  }

  /** Takes the name pattern out of [model], then hands the rest to [createDefaultMatchers]. */
  private fun createDefaultMatchers(pattern: String, model: ChooseByNameModel): ItemMatchers =
    createDefaultMatchers(pattern, ChooseByNamePopup.getTransformedPattern(pattern, model))

  private fun itemMatchers(defaultMatchers: ItemMatchers, model: ChooseByNameModel, element: Any): ItemMatchers =
    if (model is GotoFileModel && element is PsiFileSystemItem) {
      GotoFileModel.convertToFileItemMatchers(defaultMatchers, element, model)
    }
    else {
      defaultMatchers
    }

  private fun logSearchOutcome(
    outcome: String, pattern: String, stats: SeFetchStats, attemptCount: AtomicInteger, startedAtNano: Long,
  ) {
    LOG.debug {
      "$label: $outcome, pattern='$pattern', attempts=${attemptCount.get()}, " +
      "durationMs=${(System.nanoTime() - startedAtNano) / 1_000_000}, ${stats.toLogString()}"
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
  private val typeFilters: SuspendLazyProperty<List<PersistentSearchEverywhereContributorFilter<T>>> = suspendLazy {
    typeFilterProvider(project)
  }

  /**
   * The type list that the filter action at [index] shows, or an empty list when there is no such filter.
   */
  suspend fun getTypeVisibilityStates(index: Int): List<SeTypeVisibilityStatePresentation> =
    typeFilters.getValue().getOrNull(index)?.let {
      typeVisibilityStates(it)
    } ?: emptyList()

  private fun typeVisibilityStates(filter: PersistentSearchEverywhereContributorFilter<T>): List<SeTypeVisibilityStatePresentation> =
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

  private fun persistHiddenTypes(filter: PersistentSearchEverywhereContributorFilter<T>, hiddenTypes: Set<String>) {
    filter.allElements.forEach { element ->
      filter.setSelected(element, !hiddenTypes.contains(filter.getElementText(element)))
    }
  }

  //endregion

  //region Extended info

  suspend fun getExtendedInfo(item: SeTargetRawItem): SeExtendedInfo = extendedInfoCalculator.infoFor(item)

  //endregion

  //region Selection

  /**
   * Acts on the item that the user chose, and returns whether the popup should close.
   */
  suspend fun itemSelected(item: SeItem, provider: SeItemsProvider, modifiers: Int, searchText: String): Boolean {
    SeLog.log(SeLog.USER_ACTION) { "$label: item selected" }
    // A null answer means that no extension handled the item, so the popup stays open.
    return SeTargetItemSelectionProcessor.process(item, provider, modifiers, searchText) ?: false
  }

  /**
   * Runs the item through the selection chain as a selection with Shift held.
   */
  suspend fun performExtendedAction(item: SeItem, provider: SeItemsProvider): Boolean {
    SeLog.log(SeLog.USER_ACTION) { "$label: extended action" }
    return SeTargetItemSelectionProcessor.process(item, provider, InputEvent.SHIFT_DOWN_MASK, "") ?: false
  }

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
   * Builds the preview of [item], or returns null when the item shows none.
   */
  suspend fun getPreviewInfo(item: SeItem): SePreviewInfo? {
    val rawItem = (item as? SeTargetPresentableItem)?.rawItem ?: return null
    return fetchPreviewInfo(rawItem, project) { previewDisposables.add(it) }
  }

  override fun dispose() {
    previewDisposables.forEach { Disposer.dispose(it) }
    previewDisposables.clear()
  }

  //endregion

  companion object {
    private val LOG = logger<SeTargetItemsProvider<*>>()
    private const val COROUTINE_BASED_GOTO_KEY = "search.everywhere.coroutine.based.goto"

    /** How many raw items wait for the presentation stage. It lets the model search run ahead. */
    private const val RAW_ITEMS_BUFFER = 64

    /** How many presentations compute at once. */
    private const val PRESENTATION_CONCURRENCY = 10

    suspend fun isCoroutineBasedGotoEnabled(legacyContributor: SearchEverywhereContributor<Any>, providerId: String): Boolean {
      if (!RegistryManager.getInstanceAsync().`is`(COROUTINE_BASED_GOTO_KEY)) return false

      val effectiveContributor = (legacyContributor as? SearchEverywhereContributorWrapper)?.getEffectiveContributor()
                                 ?: legacyContributor
      if (effectiveContributor is SemanticSearchEverywhereContributor && !ApplicationManager.getApplication().isInternal) {
        SeLog.log(SeLog.LIFE_CYCLE) { "$providerId: the semantic contributor is on, keeping the legacy provider" }
        return false
      }

      return true
    }

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
     * Builds the matchers of the whole query, before any item narrows them.
     */
    fun createDefaultMatchers(rawPattern: String, namePattern: String): ItemMatchers {
      val fullRawPattern = "*$rawPattern"
      val fullNamePattern = "*$namePattern"

      val matcherFactory = ChooseByNameMatcherFactory.tryGetInstance()
      if (matcherFactory != null) {
        val rawMatcher = matcherFactory.createMatcher(fullRawPattern, false)
        val nameMatcher = matcherFactory.createMatcher(fullNamePattern, false)
        if (rawMatcher != null && nameMatcher != null) {
          val matcher = if (fullRawPattern == fullNamePattern) rawMatcher else MatcherWithFallback(rawMatcher, nameMatcher)
          return ItemMatchers(matcher, null)
        }
      }

      return ItemMatchers(NameUtil.buildMatcherWithFallback(fullRawPattern, fullNamePattern, MatchingMode.IGNORE_CASE), null)
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

    suspend fun <T> create(
      project: Project,
      dataContext: DataContext,
      operationDisposable: Disposable?,
      label: String,
      gotoModelProvider: (Project, ScopeDescriptor?, Set<T>) -> (FilteringGotoByModel<*>),
      typeFilterProvider: (Project) -> List<PersistentSearchEverywhereContributorFilter<T>> = { emptyList() },
      extendedInfoCalculator: SeExtendedInfoCalculator = SePsiExtendedInfoCalculator(),
      isFileProvider: Boolean = false,
    ): SeTargetItemsProvider<T> {
      val psiContext = readAction {
        GotoActionBase.getPsiContext(dataContext)?.let { context ->
          SmartPointerManager.getInstance(project).createSmartPsiElementPointer(context)
        }
      }

      return SeTargetItemsProvider(project, psiContext, operationDisposable, label, gotoModelProvider, typeFilterProvider,
                                   extendedInfoCalculator, isFileProvider)
    }
  }
}

/**
 * The counters and the timings of one search, for the performance log.
 *
 * A clock reads only when [isEnabled] is true, so a search that does not log pays almost nothing.
 * A summed time comes from several coroutines at once, so it can pass the wall time of the search.
 */
private class SeFetchStats(
  /** False turns every clock off, so a search that does not log pays one boolean test per item. */
  val isEnabled: Boolean,
) {

  /** The items that the model produced. It counts an item again after a read action restart. */
  val fromModelCount: AtomicInteger = AtomicInteger(0)

  /** The items that reached the channel. */
  val sentCount: AtomicInteger = AtomicInteger(0)

  val scopeResolveNanos: AtomicLong = AtomicLong(0)
  val modelCreateNanos: AtomicLong = AtomicLong(0)
  val modelSearchNanos: AtomicLong = AtomicLong(0)

  /**
   * The time that `send` waited for the consumer.
   *
   * The search holds the read lock while it waits, so this time adds to the risk of a read action
   * restart. A large value means that the consumer of the flow is the bottleneck, not the model.
   */
  val blockedInSendNanos: AtomicLong = AtomicLong(0)

  /** The delay before the first item reached the channel. This is the latency that the user sees. */
  val firstItemNanos: AtomicLong = AtomicLong(0)

  inline fun <T> measure(target: AtomicLong, action: () -> T): T {
    if (!isEnabled) return action()

    val startedAt = System.nanoTime()
    try {
      return action()
    }
    finally {
      target.addAndGet(System.nanoTime() - startedAt)
    }
  }

  fun recordFirstItem(startedAtNano: Long) {
    if (isEnabled) firstItemNanos.compareAndSet(0, System.nanoTime() - startedAtNano)
  }

  fun toLogString(): String =
    "elementsFromModel=${fromModelCount.get()}, sent=${sentCount.get()}, " +
    "firstItemMs=${firstItemNanos.get() / 1_000_000}, scopesMs=${scopeResolveNanos.get() / 1_000_000}, " +
    "modelMs=${modelCreateNanos.get() / 1_000_000}, searchMs=${modelSearchNanos.get() / 1_000_000}, " +
    "blockedInSendMs=${blockedInSendNanos.get() / 1_000_000}"
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
