// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.text

import com.intellij.find.FindBundle
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.FindSettings
import com.intellij.find.impl.FindInProjectUtil
import com.intellij.find.impl.SearchEverywhereItem
import com.intellij.find.impl.usagePresentation
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereNavigationHandler
import com.intellij.ide.actions.searcheverywhere.footer.createTextExtendedInfo
import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.toSerializableTextChunk
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.backend.navigation.NavigationRequests
import com.intellij.platform.scopes.SearchScopesInfo
import com.intellij.platform.searchEverywhere.SeExtendedInfo
import com.intellij.platform.searchEverywhere.SeExtendedInfoBuilder
import com.intellij.platform.searchEverywhere.SeExtendedInfoProvider
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsPreviewProvider
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SePreviewInfoFactory
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeSearchScopesProvider
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.presentations.SeTextSearchItemPresentation
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilterImpl
import com.intellij.platform.searchEverywhere.providers.SeTextFilter
import com.intellij.platform.searchEverywhere.providers.target.SeTargetScopes
import com.intellij.platform.searchEverywhere.providers.target.SeTargetScopesProvider
import com.intellij.platform.searchEverywhere.providers.target.createTargetScopes
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.suspendLazy
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PredefinedSearchScopeProviderImpl
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageViewPresentation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.InputEvent
import java.lang.ref.Reference
import java.lang.ref.WeakReference

/**
 * A found line of the text search.
 */
@ApiStatus.Internal
class SeTextSearchItem(
  val item: SearchEverywhereItem,
  private val weight: Int,
  val extendedInfo: SeExtendedInfo,
) : SeItem {
  override val rawObject: Any get() = item
  override fun weight(): Int = weight

  override suspend fun presentation(): SeItemPresentation =
    SeTextSearchItemPresentation(item.presentableText,
                                 extendedInfo,
                                 item.presentation.text.map { chunk ->
                                   chunk.toSerializableTextChunk()
                                 },
                                 item.presentation.backgroundColor?.rpcId(),
                                 item.presentation.fileString,
                                 isMultiSelectionSupported = false)
}

@ApiStatus.Internal
class SeTextItemsProvider(
  private val project: Project,
  private val psiContext: SmartPsiElementPointer<PsiElement?>?,
) : SeItemsProvider, SeSearchScopesProvider, SeItemsPreviewProvider, SeExtendedInfoProvider {
  override val id: String get() = SeProviderIdUtils.TEXT_ID
  override val displayName: @Nls String get() = FindBundle.message("search.everywhere.group.name")

  private val findModel = FindManager.getInstance(project).findInProjectModel
  private val extendedInfo = createTextExtendedInfo()
  private val navigationHandler = TextSearchNavigationHandler(project)

  private val scopes: SuspendLazyProperty<SeTargetScopes> = suspendLazy { createScopes() }

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val query = params.inputQuery
    if (query.isEmpty()) return

    val searchModel = createSearchModel(query, params.filter)

    coroutineToIndicator { indicator ->
      findItems(searchModel, indicator) { item ->
        val seItem = SeTextSearchItem(item, 0, SeExtendedInfoBuilder().withExtendedInfo(extendedInfo, item).build())
        runBlockingCancellable { collector.put(seItem) }
      }
    }
  }

  /**
   * Returns a snapshot of the shared find model, set up for the search of [query] with [filter].
   *
   * Each found usage keeps the model of its search and searches through it again to check its validity.
   * So the search must not use the shared model, because the user can change the shared model later.
   */
  private suspend fun createSearchModel(query: String, filter: SeFilterState): FindModel {
    val textFilter = SeTextFilter.from(filter)
    val isEverywhere = SeEverywhereFilterImpl.isEverywhere(filter)
    val scopes = scopes.getValue()

    var isScopeSelectedByUser = false
    val scope: ScopeDescriptor? = when {
      // The welcome-screen project has no source. A project-wide text search finds nothing and costs a full scan.
      // The recent-files scope holds the editor history plus the open files, which is the only useful set there.
      WelcomeScreenProjectProvider.isWelcomeScreenProject(project) ->
        scopes.byName(PredefinedSearchScopeProviderImpl.getRecentlyViewedFilesScopeName())
      isEverywhere != null -> scopes.byId[isEverywhere]
      else -> textFilter?.selectedScopeId?.let { scopes.byId[it] }?.also { isScopeSelectedByUser = true }
              ?: scopes.info?.selectedScopeId?.let { scopes.byId[it] }
    }

    if (SeEverywhereFilterImpl.isAllTab(filter) == true) {
      return findModel.clone().apply {
        fileFilter = null
        isCaseSensitive = false
        isWholeWordsOnly = false
        isRegularExpressions = false
        scope?.let { applyScope(this, it) }
        FindModel.initStringToFind(this, query)
      }
    }

    if (textFilter != null) {
      findModel.fileFilter = textFilter.selectedType
      findModel.isCaseSensitive = textFilter.isCaseSensitive
      findModel.isWholeWordsOnly = textFilter.isWholeWordsOnly
      findModel.isRegularExpressions = textFilter.isRegex
      FindSettings.getInstance().fileMask = textFilter.selectedType
    }

    if (scope != null) {
      if (isScopeSelectedByUser) {
        val isToggleScope = scope === scopes.byId[true] || scope === scopes.byId[false]
        project.putUserData(SELECTED_SCOPE_NAME, if (isToggleScope) null else scope.displayName)
        FindSettings.getInstance().customScope = scope.scope?.displayName
      }
      applyScope(findModel, scope)
    }

    FindModel.initStringToFind(findModel, query)
    return findModel.clone()
  }

  /**
   * Finds the lines with the text of [model]. The usages of one line become one item.
   */
  private fun findItems(model: FindModel, indicator: ProgressIndicator, consumer: (SearchEverywhereItem) -> Boolean) {
    val presentation = FindInProjectUtil.setupProcessPresentation(UsageViewPresentation())
    val pathScope = GlobalSearchScope.projectScope(project)
    val recentItemRef = ThreadLocal<Reference<SearchEverywhereItem>>()

    FindInProjectUtil.findUsages(model, project, indicator, presentation, emptySet()) {
      val usage = UsageInfo2UsageAdapter.CONVERTER.`fun`(it) as UsageInfo2UsageAdapter
      indicator.checkCanceled()

      val recentItem = recentItemRef.get()?.get()
      val newItem = if (recentItem != null && recentItem.usage.merge(usage)) {
        recentItem.usage.updateCachedPresentation()
        recentItem.withPresentation(usagePresentation(project, pathScope, recentItem.usage))
      }
      else {
        usage.updateCachedPresentation()
        SearchEverywhereItem(usage, usagePresentation(project, pathScope, usage)).also {
          if (!consumer(it)) return@findUsages false
        }
      }
      recentItemRef.set(WeakReference(newItem))

      true
    }
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val textItem = (item as? SeTextSearchItem)?.item ?: return false
    return withContext(Dispatchers.EDT) {
      navigate(textItem, modifiers, searchText)
    }
  }

  override suspend fun performExtendedAction(item: SeItem): Boolean {
    val textItem = (item as? SeTextSearchItem)?.item ?: return false
    return withContext(Dispatchers.EDT) {
      navigate(textItem, InputEvent.SHIFT_DOWN_MASK, "")
    }
  }

  private fun navigate(item: SearchEverywhereItem, modifiers: Int, searchText: String): Boolean {
    val usage = item.usage
    if (!usage.canNavigate()) return false
    val element = usage.usageInfo.element ?: return false

    navigationHandler.gotoSelectedItem(element, modifiers, searchText, usage.navigationOffset)
    return true
  }

  override suspend fun canBeShownInFindResults(): Boolean = isEnabled()

  override suspend fun getSearchScopesInfo(): SearchScopesInfo? = scopes.getValue().info

  override suspend fun getPreviewInfo(item: SeItem, project: Project): SePreviewInfo? {
    val textItem = (item as? SeTextSearchItem)?.item ?: return null
    val navigationOffsets = textItem.usage.mergedInfos.map { it.navigationRange.startOffset to it.navigationRange.endOffset }

    return SePreviewInfoFactory.create(textItem.usage.file.rpcId(), navigationOffsets)
  }

  override fun addDataForItem(item: SeItem, sink: DataSink) {
    val textItem = (item as? SeTextSearchItem)?.item ?: return
    sink.lazy(CommonDataKeys.PSI_ELEMENT) { textItem.usage.element }
  }

  override fun getPsiElementForItem(item: SeItem): PsiElement? = getData(PlatformCoreDataKeys.PSI_ELEMENT, item)

  override fun getVirtualFileForItem(item: SeItem): VirtualFile? = getData(PlatformCoreDataKeys.VIRTUAL_FILE, item)

  override fun getNavigatableForItem(item: SeItem): Navigatable? = getData(PlatformCoreDataKeys.NAVIGATABLE, item)

  private fun <T : Any> getData(key: DataKey<T>, item: SeItem): T? =
    CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink -> addDataForItem(item, sink) }.getData(key)

  override fun dispose() {}

  /**
   * Builds the scope list. The selected scope is the scope that the user selected last, or the project scope.
   */
  private suspend fun createScopes(): SeTargetScopes {
    val scopes = createTargetScopes(project, psiContext, SeTargetScopesProvider.DEFAULT, "SeText")
    val info = scopes.info ?: return scopes

    val selectedScopeName = project.getUserData(SELECTED_SCOPE_NAME)
    val selectedScopeId = selectedScopeName?.let { name -> info.scopes.firstOrNull { !it.isSeparator && it.name == name }?.scopeId }
                          ?: info.projectScopeId

    return SeTargetScopes(SearchScopesInfo(info.scopes, selectedScopeId, info.projectScopeId, info.everywhereScopeId), scopes.byId)
  }

  private fun SeTargetScopes.byName(name: @Nls String): ScopeDescriptor? =
    info?.scopes?.firstOrNull { !it.isSeparator && it.name == name }?.let { byId[it.scopeId] }

  companion object {
    private const val ADVANCED_OPTION_ID = "se.text.search"

    /**
     * The name of the scope that the user selected last in the Text tab.
     * The project scope and the everywhere scope are not kept, so a new session starts on the project scope.
     */
    private val SELECTED_SCOPE_NAME = Key.create<String>("SeTextItemsProvider.selectedScopeName")

    /**
     * Returns `true` when the advanced settings turn on the text search in Search Everywhere.
     */
    fun isEnabled(): Boolean = AdvancedSettings.getBoolean(ADVANCED_OPTION_ID)

    /**
     * Writes [scope] into [model]. The model then searches only in [scope], not in a directory or a module.
     */
    private fun applyScope(model: FindModel, scope: ScopeDescriptor) {
      model.customScopeName = scope.scope?.displayName
      model.customScope = scope.scope
      model.directoryName = null
      model.isCustomScope = true
    }
  }
}

/**
 * Opens the file of a found line at the offset of the line.
 */
private class TextSearchNavigationHandler(project: Project) : SearchEverywhereNavigationHandler(project) {
  override suspend fun createSourceNavigationRequest(
    project: Project,
    element: PsiElement,
    file: VirtualFile,
    searchText: String,
    offset: Int,
  ): NavigationRequest? {
    val navigationRequests = serviceAsync<NavigationRequests>()
    return readAction {
      navigationRequests.sourceNavigationRequest(project = project, file = file, offset = offset, elementRange = null)
    }
  }
}
