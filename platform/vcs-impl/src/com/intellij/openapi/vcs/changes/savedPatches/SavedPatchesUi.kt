// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager
import com.intellij.ui.*
import com.intellij.util.containers.orNull
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ProportionKey
import com.intellij.util.ui.TwoKeySplitter
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.commit.CommitActionsPanel
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.border.CompoundBorder

open class SavedPatchesUi(project: Project,
                          private val providers: List<SavedPatchesProvider<*>>,
                          private val isVertical: () -> Boolean,
                          private val isEditorDiffPreview: () -> Boolean,
                          focusMainUi: (Component?) -> Unit,
                          disposable: Disposable) :
  JPanel(BorderLayout()), Disposable, DataProvider {

  protected val tree: SavedPatchesTree
  internal val changesBrowser: SavedPatchesChangesBrowser
  private val treeChangesSplitter: TwoKeySplitter
  private val treeDiffSplitter: OnePixelSplitter

  init {
    tree = SavedPatchesTree(project, providers, this)
    PopupHandler.installPopupMenu(tree, "Vcs.SavedPatches.ContextMenu", SAVED_PATCHES_UI_PLACE)

    changesBrowser = SavedPatchesChangesBrowser(project, focusMainUi, this)
    CombinedSpeedSearch(changesBrowser.viewer, tree.speedSearch)

    val bottomToolbar = buildBottomToolbar()

    tree.addSelectionListener {
      changesBrowser.selectPatchObject(selectedPatchObjectOrNull())
      bottomToolbar.updateActionsImmediately()
    }
    tree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY) { bottomToolbar.updateActionsImmediately() }

    val treePanel = JPanel(BorderLayout())
    treePanel.add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER)

    treeChangesSplitter = TwoKeySplitter(isVertical(),
                                         ProportionKey("vcs.saved.patches.changes.splitter.vertical", 0.5f,
                                                       "vcs.saved.patches.changes.splitter.horizontal", 0.5f))
    treeChangesSplitter.firstComponent = treePanel
    treeChangesSplitter.secondComponent = BorderLayoutPanel().apply {
      addToCenter(changesBrowser)
      addToBottom(createBottomComponent(bottomToolbar))
    }
    providers.forEach { provider ->
      provider.subscribeToPatchesListChanges(this) {
        treeChangesSplitter.secondComponent.isVisible = providers.any { !it.isEmpty() }
      }
    }
    treeChangesSplitter.secondComponent.isVisible = providers.any { !it.isEmpty() }

    treeDiffSplitter = OnePixelSplitter("vcs.saved.patches.diff.splitter", 0.5f)
    treeDiffSplitter.firstComponent = treeChangesSplitter

    updateLayout(isInitial = true)

    add(treeDiffSplitter, BorderLayout.CENTER)

    Disposer.register(disposable, this)
  }

  private fun createBottomComponent(bottomToolbar: ActionToolbar): JComponent {
    val bottomPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
      border = CompoundBorder(IdeBorderFactory.createBorder(SideBorder.TOP),
                              JBUI.Borders.empty(7, 5))
    }
    bottomPanel.add(bottomToolbar.component)
    bottomPanel.add(JLabel(AllIcons.General.ContextHelp).apply {
      border = JBUI.Borders.empty(1)
      toolTipText = VcsBundle.message("saved.patch.apply.pop.help.tooltip")
    })
    return bottomPanel
  }

  private fun buildBottomToolbar(): ActionToolbar {
    val applyAction = object : JButtonActionWrapper(VcsBundle.message("saved.patch.apply.action"), true) {
      override fun getDelegate(): AnAction {
        return selectedProvider().applyAction
      }
    }.apply {
      registerCustomShortcutSet(CommitActionsPanel.DEFAULT_COMMIT_ACTION_SHORTCUT, this@SavedPatchesUi, this@SavedPatchesUi)
    }
    val popAction = object : JButtonActionWrapper(VcsBundle.message("saved.patch.pop.action"), false) {
      override fun getDelegate(): AnAction {
        return selectedProvider().popAction
      }
    }
    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.add(applyAction)
    toolbarGroup.add(popAction)
    val toolbar = ActionManager.getInstance().createActionToolbar(SAVED_PATCHES_UI_PLACE, toolbarGroup, true)
    toolbar.targetComponent = tree
    return toolbar
  }

  fun updateLayout() {
    updateLayout(isInitial = false)
  }

  private fun updateLayout(isInitial: Boolean) {
    val isVertical = isVertical()
    val isEditorDiffPreview = isEditorDiffPreview()
    val isInEditor = isEditorDiffPreview || isVertical
    val isChangesSplitterVertical = !isEditorDiffPreview || isVertical
    if (treeChangesSplitter.orientation != isChangesSplitterVertical) {
      treeChangesSplitter.orientation = isChangesSplitterVertical
    }
    setDiffPreviewInEditor(isInEditor, isInitial)
  }

  private fun setDiffPreviewInEditor(isInEditor: Boolean, isInitial: Boolean) {
    val needUpdatePreviews = isInEditor != (changesBrowser.editorTabPreview != null)
    if (!isInitial && !needUpdatePreviews) return

    val diffPreviewProcessor = changesBrowser.installDiffPreview(isInEditor)
    if (isInEditor) {
      treeDiffSplitter.secondComponent = null
    }
    else {
      treeDiffSplitter.secondComponent = diffPreviewProcessor.component
    }
  }

  override fun dispose() {
  }

  override fun getData(dataId: String): Any? {
    if (EditorTabDiffPreviewManager.EDITOR_TAB_DIFF_PREVIEW.`is`(dataId)) return changesBrowser.editorTabPreview
    if (SAVED_PATCH_SELECTED_PATCH.`is`(dataId)) return selectedPatchObjectOrNull()
    if (SAVED_PATCHES_UI.`is`(dataId)) return this
    if (SAVED_PATCH_CHANGES.`is`(dataId)) return changesBrowser.getData(dataId)
    return null
  }

  private fun selectedPatchObjectOrNull() = tree.selectedPatchObjects().findAny().orNull()

  private fun selectedProvider(): SavedPatchesProvider<*> {
    val selectedPatch = selectedPatchObjectOrNull() ?: return providers.first()
    return providers.find { it.dataClass.isInstance(selectedPatch.data) } ?: return providers.first()
  }

  companion object {
    const val SAVED_PATCHES_UI_PLACE = "SavedPatchesUiPlace"
    val SAVED_PATCHES_UI = DataKey.create<SavedPatchesUi>("SavedPatchesUi")
    val SAVED_PATCH_CHANGES = DataKey.create<Iterable<SavedPatchesProvider.ChangeObject>>("SavedPatchChanges")
    val SAVED_PATCH_SELECTED_CHANGES = DataKey.create<Iterable<SavedPatchesProvider.ChangeObject>>("SavedPatchSelectedChanges")
    val SAVED_PATCH_SELECTED_PATCH = DataKey.create<SavedPatchesProvider.PatchObject<*>>("SavedPatchSelectedPatches")
  }
}
