// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.nonIndexableFiles

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.ExtendedInfo
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.FileSearchEverywhereNavigationContributionHandler
import com.intellij.ide.actions.searcheverywhere.PSIPresentationBgRendererWrapper
import com.intellij.ide.actions.searcheverywhere.footer.createPsiExtendedInfo
import com.intellij.ide.util.PsiElementListCellRenderer.ItemMatchers
import com.intellij.ide.util.gotoByName.DefaultChooseByNameItemProvider
import com.intellij.ide.util.gotoByName.FileTypeRef
import com.intellij.ide.util.scopeChooser.ScopeDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.searchEverywhere.SeComposedWeight
import com.intellij.platform.searchEverywhere.SeExtendedInfoBuilder
import com.intellij.platform.searchEverywhere.SeExtendedInfoProvider
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsPreviewProvider
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeTypeFilterKeys
import com.intellij.platform.searchEverywhere.providers.SeEverywhereFilterImpl
import com.intellij.platform.searchEverywhere.providers.SeScopeByIdFiles
import com.intellij.platform.searchEverywhere.providers.target.SeScopeById
import com.intellij.platform.searchEverywhere.providers.target.SeTargetItemsProvider
import com.intellij.platform.searchEverywhere.providers.target.SeTargetPresentableItem
import com.intellij.platform.searchEverywhere.providers.target.SeTargetPresentableItemImpl
import com.intellij.platform.searchEverywhere.providers.target.SeTargetsFilter
import com.intellij.platform.searchEverywhere.providers.target.presentation.SeTargetPresentationProvider
import com.intellij.platform.searchEverywhere.utils.SuspendLazyProperty
import com.intellij.platform.searchEverywhere.utils.suspendLazy
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.SmartPsiElementPointer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.event.InputEvent
import java.util.concurrent.ConcurrentLinkedQueue

@ApiStatus.Internal
class SeNonIndexableFilesProvider(
  private val project: Project,
  private val psiContext: SmartPsiElementPointer<PsiElement?>?,
) : SeItemsProvider, SeItemsPreviewProvider, SeExtendedInfoProvider {
  override val id: String
    get() = SeProviderIdUtils.NON_INDEXABLE_FILES_ID
  override val displayName: @Nls String
    get() = IdeBundle.message("search.everywhere.group.name.non.indexable.files")

  private val navigationHandler = FileSearchEverywhereNavigationContributionHandler(project)

  private val filesSearch = SeNonIndexableFilesSearch(project)

  /** The scope ids of the Files provider end with the serialization id of the scope, so this map resolves them. */
  private val scopeById: SuspendLazyProperty<SeScopeById> = suspendLazy { readAction { SeScopeByIdFiles(project, psiContext) } }

  private val extendedInfo: ExtendedInfo by lazy(LazyThreadSafetyMode.PUBLICATION) {
    createPsiExtendedInfo(psiElement = { PSIPresentationBgRendererWrapper.toPsi(it) },
                          shouldShowRightAction = true,
                          fallbackToContentFileSetRoot = true)
  }

  /** The disposables that a preview fetch opens. [dispose] releases them. */
  private val previewDisposables = ConcurrentLinkedQueue<Disposable>()

  override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {
    val inputQuery = SeTargetItemsProvider.normalizeQuery(params.inputQuery)
    if (inputQuery.isEmpty()) return

    val [scope, hiddenTypes] = scopeAndHiddenTypes(params.filter)
    val matchers = SeTargetItemsProvider.createDefaultMatchers(inputQuery, inputQuery)

    filesSearch.search(inputQuery, scope?.scope, hiddenTypes) { item, matchingDegree ->
      collector.put(createItem(item, matchingDegree, matchers, inputQuery))
    }
  }

  private suspend fun scopeAndHiddenTypes(filter: SeFilterState): Pair<ScopeDescriptor?, Set<FileTypeRef>> {
    SeEverywhereFilterImpl.isEverywhere(filter)?.let { isEverywhere ->
      return scopeById.getValue()[isEverywhere] to emptySet()
    }

    val targetsFilter = SeTargetsFilter.from(filter)
    val scope = targetsFilter.selectedScopeId?.let { scopeById.getValue()[it] }
    val hiddenTypeNames = targetsFilter.hiddenTypes[SeTypeFilterKeys.DEFAULT].orEmpty().toSet()
    val hiddenTypes = if (hiddenTypeNames.isEmpty()) emptySet()
    else FileSearchEverywhereContributor.getAllFileTypes().filterTo(HashSet()) { it.displayName in hiddenTypeNames }

    return scope to hiddenTypes
  }

  private suspend fun createItem(item: PsiFileSystemItem, matchingDegree: Int, matchers: ItemMatchers, inputQuery: String): SeItem {
    val presentation = SeTargetPresentationProvider.computePresentation(item)
                       ?: TargetPresentation.builder(item.name).presentation()
    val itemExtendedInfo = readAction { SeExtendedInfoBuilder().withExtendedInfo(extendedInfo, item).build() }

    return SeTargetPresentableItemImpl(
      rawItem = item,
      matchers = matchers,
      composedWeight = SeComposedWeight(matchingDegree),
      presentation = presentation,
      extendedInfo = itemExtendedInfo,
      isMultiSelectionSupported = false,
      isExactMatch = SeTargetItemsProvider.isExactMatch(
        isExactMatchFromItem = DefaultChooseByNameItemProvider.isInExactMatchDegreeRange(matchingDegree),
        presentableText = presentation.presentableText,
        inputQuery = inputQuery,
        isFile = false,
        inputQueryHasNoExtension = !inputQuery.contains('.'),
        isDirectory = item is PsiDirectory,
      ),
    )
  }

  override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean {
    val psiItem = validPsiElementOf(item) ?: return false
    // The handler launches the navigation on its own scope, so this returns before the file opens.
    navigationHandler.gotoSelectedItem(psiItem, modifiers, searchText)
    return true
  }

  override suspend fun performExtendedAction(item: SeItem): Boolean =
    itemSelected(item, InputEvent.SHIFT_DOWN_MASK, "")

  override suspend fun canBeShownInFindResults(): Boolean = true

  override suspend fun getPreviewInfo(item: SeItem, project: Project): SePreviewInfo? {
    val rawObject = (item as? SeTargetPresentableItem)?.rawObject ?: return null
    return SeTargetItemsProvider.fetchPreviewInfo(rawObject, project) { previewDisposables.add(it) }
  }

  override fun addDataForItem(item: SeItem, sink: DataSink) {
    val rawObject = (item as? SeTargetPresentableItem)?.rawObject ?: return
    sink.lazy(CommonDataKeys.PSI_ELEMENT) { PSIPresentationBgRendererWrapper.toPsi(rawObject) }
  }

  override fun getPsiElementForItem(item: SeItem): PsiElement? = getData(CommonDataKeys.PSI_ELEMENT, item)

  override fun getVirtualFileForItem(item: SeItem): VirtualFile? = getData(CommonDataKeys.VIRTUAL_FILE, item)

  override fun getNavigatableForItem(item: SeItem): Navigatable? = getData(CommonDataKeys.NAVIGATABLE, item)

  private fun <T : Any> getData(key: DataKey<T>, item: SeItem): T? =
    CustomizedDataContext.withSnapshot(DataContext.EMPTY_CONTEXT) { sink -> addDataForItem(item, sink) }.getData(key)

  private suspend fun validPsiElementOf(item: SeItem): PsiElement? {
    val rawObject = (item as? SeTargetPresentableItem)?.rawObject ?: return null
    return readAction { PSIPresentationBgRendererWrapper.toPsi(rawObject)?.takeIf { it.isValid } }
  }

  override fun dispose() {
    previewDisposables.forEach { Disposer.dispose(it) }
    previewDisposables.clear()
  }
}
