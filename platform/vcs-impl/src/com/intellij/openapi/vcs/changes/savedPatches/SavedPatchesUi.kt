// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.shouldHaveSplitterDiffPreview
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import com.intellij.util.ui.ProportionKey
import com.intellij.util.ui.TwoKeySplitter
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.ScrollPaneConstants

@ApiStatus.Internal
open class SavedPatchesUi(
  private val project: Project,
  val providers: List<SavedPatchesProvider<*>>,
  private val isVertical: () -> Boolean,
  private val isWithSplitDiffPreview: () -> Boolean,
  private val isShowDiffWithLocal: () -> Boolean,
  focusMainUi: (Component?) -> Unit,
  disposable: Disposable,
) :
  JPanel(BorderLayout()), Disposable, UiDataProvider {

  protected val patchesTree: SavedPatchesTree
  internal val changesBrowser: SavedPatchesChangesBrowser
  private val treeChangesSplitter: TwoKeySplitter
  private val treeDiffSplitter: OnePixelSplitter

  private val visibleProviders = providers.toMutableSet()

  private val editorTabPreview: SavedPatchesEditorDiffPreview
  private var splitDiffProcessor: SavedPatchesDiffProcessor? = null

  init {
    patchesTree = SavedPatchesTree(project, providers, visibleProviders::contains, this)
    PopupHandler.installPopupMenu(patchesTree, "Vcs.SavedPatches.ContextMenu", SAVED_PATCHES_UI_PLACE)

    changesBrowser = SavedPatchesChangesBrowser(project, isShowDiffWithLocal, this)
    CombinedSpeedSearch(changesBrowser.viewer, patchesTree.speedSearch)

    editorTabPreview = SavedPatchesEditorDiffPreview(changesBrowser, focusMainUi, isShowDiffWithLocal)
    changesBrowser.setShowDiffActionPreview(editorTabPreview)

    patchesTree.doubleClickHandler = Processor { e ->
      if (EditSourceOnDoubleClickHandler.isToggleEvent(patchesTree, e)) return@Processor false
      changesBrowser.showDiff()
      return@Processor true
    }
    patchesTree.enterKeyHandler = Processor {
      changesBrowser.showDiff()
      return@Processor true
    }

    val bottomToolbar = SavedPatchesComponents.buildBottomToolbar(patchesTree, ::selectedProvider, this, this)

    patchesTree.addSelectionListener {
      changesBrowser.selectPatchObject(selectedPatchObjectOrNull())
      bottomToolbar.updateActionsImmediately()
    }
    patchesTree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY) { bottomToolbar.updateActionsImmediately() }
    patchesTree.accessibleContext.accessibleName = VcsBundle.message("stash.tree.accessible.name")

    val treePanel = JPanel(BorderLayout())
    val scrollPane = ScrollPaneFactory.createScrollPane(patchesTree, true)
    scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    treePanel.add(scrollPane, BorderLayout.CENTER)

    treeChangesSplitter = TwoKeySplitter(isVertical(),
                                         ProportionKey("vcs.saved.patches.changes.splitter.vertical", 0.5f,
                                                       "vcs.saved.patches.changes.splitter.horizontal", 0.5f))
    treeChangesSplitter.firstComponent = treePanel
    treeChangesSplitter.secondComponent = BorderLayoutPanel().apply {
      addToCenter(changesBrowser)
      addToBottom(SavedPatchesComponents.createBottomComponent(bottomToolbar))
    }
    providers.forEach { provider ->
      provider.subscribeToPatchesListChanges(this) {
        treeChangesSplitter.secondComponent.isVisible = providers.any { visibleProviders.contains(it) && !it.isEmpty() }
      }
    }
    treeChangesSplitter.secondComponent.isVisible = providers.any { visibleProviders.contains(it) && !it.isEmpty() }

    treeDiffSplitter = OnePixelSplitter("vcs.saved.patches.diff.splitter", 0.5f)
    treeDiffSplitter.firstComponent = treeChangesSplitter

    updateLayout(isInitial = true)

    add(treeDiffSplitter, BorderLayout.CENTER)

    Disposer.register(disposable, this)
  }

  fun updateLayout() {
    updateLayout(isInitial = false)
  }

  private fun updateLayout(isInitial: Boolean) {
    val isVertical = isVertical()
    val isWithSplitPreview = shouldHaveSplitterDiffPreview(project, isVertical) && isWithSplitDiffPreview()
    val isChangesSplitterVertical = isVertical || isWithSplitPreview
    if (treeChangesSplitter.orientation != isChangesSplitterVertical) {
      treeChangesSplitter.orientation = isChangesSplitterVertical
    }
    setWithSplitDiffPreview(isWithSplitPreview, isInitial)
  }

  private fun setWithSplitDiffPreview(isWithSplitPreview: Boolean, isInitial: Boolean) {
    val needUpdatePreviews = isWithSplitPreview != (splitDiffProcessor != null)
    if (!isInitial && !needUpdatePreviews) return

    if (isWithSplitPreview) {
      val processor = SavedPatchesDiffProcessor(changesBrowser.viewer, false, isShowDiffWithLocal)
      splitDiffProcessor = processor
      treeDiffSplitter.secondComponent = processor.component
    }
    else {
      splitDiffProcessor?.let { Disposer.dispose(it) }
      splitDiffProcessor = null
      treeDiffSplitter.secondComponent = null
    }
  }

  override fun dispose() {
    Disposer.dispose(editorTabPreview)

    splitDiffProcessor?.let { Disposer.dispose(it) }
    splitDiffProcessor = null
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[DiffDataKeys.EDITOR_TAB_DIFF_PREVIEW] = editorTabPreview
    sink[SAVED_PATCH_SELECTED_PATCH] = selectedPatchObjectOrNull()
    sink[SAVED_PATCHES_UI] = this
    sink[SAVED_PATCH_CHANGES] = changesBrowser.getSavedPatchChanges()
    sink[SAVED_PATCHES_BROWSER] = changesBrowser
  }

  private fun selectedPatchObjectOrNull() = patchesTree.selectedPatchObjects().firstOrNull()

  private fun selectedProvider(): SavedPatchesProvider<*> {
    val selectedPatch = selectedPatchObjectOrNull() ?: return providers.first()
    return providers.find { it.dataClass.isInstance(selectedPatch.data) } ?: return providers.first()
  }

  fun showFirstUnderProvider(provider: SavedPatchesProvider<*>) {
    patchesTree.invokeAfterRefresh { patchesTree.showFirstUnderProvider(provider) }
  }

  fun showFirstUnderObject(provider: SavedPatchesProvider<*>, userObject: Any) {
    patchesTree.invokeAfterRefresh { patchesTree.showFirstUnderObject(provider, userObject) }
  }

  @ApiStatus.Internal
  fun setVisibleProviders(newVisibleProviders: Collection<SavedPatchesProvider<*>>) {
    visibleProviders.clear()
    visibleProviders.addAll(newVisibleProviders)
    patchesTree.rebuildTree()
  }

  companion object {
    const val SAVED_PATCHES_UI_PLACE = "SavedPatchesUiPlace"
    val SAVED_PATCHES_UI = DataKey.create<JComponent>("SavedPatchesUi")
    val SAVED_PATCHES_BROWSER = DataKey.create<SavedPatchesChangesBrowser>("SavedPatchesChangesBrowser")
    val SAVED_PATCH_CHANGES = DataKey.create<Iterable<SavedPatchesProvider.ChangeObject>>("SavedPatchChanges")
    val SAVED_PATCH_SELECTED_CHANGES = DataKey.create<Iterable<SavedPatchesProvider.ChangeObject>>("SavedPatchSelectedChanges")
    val SAVED_PATCH_SELECTED_PATCH = DataKey.create<SavedPatchesProvider.PatchObject<*>>("SavedPatchSelectedPatches")
  }
}
