// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager
import com.intellij.ui.*
import com.intellij.util.containers.orNull
import com.intellij.util.ui.ProportionKey
import com.intellij.util.ui.TwoKeySplitter
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.commit.CommitActionsPanel
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JPanel
import javax.swing.JTree

class SavedPatchesUi(project: Project, private val providers: List<SavedPatchesProvider<*>>,
                     isVertical: Boolean, isEditorDiffPreview: Boolean,
                     focusMainUi: (Component?) -> Unit, disposable: Disposable) :
  JPanel(BorderLayout()), Disposable, DataProvider {

  private val tree: SavedPatchesTree
  internal val changesBrowser: SavedPatchesChangesBrowser
  private val treeChangesSplitter: TwoKeySplitter
  private val treeDiffSplitter: OnePixelSplitter

  init {
    tree = SavedPatchesTree(project, providers, this)
    PopupHandler.installPopupMenu(tree, "Vcs.SavedPatches.ContextMenu", SAVED_PATCHES_UI_PLACE)

    changesBrowser = SavedPatchesChangesBrowser(project, focusMainUi, this)
    val bottomToolbar = buildBottomToolbar()

    tree.addSelectionListener {
      changesBrowser.selectPatchObject(selectedPatchObjectOrNull())
      bottomToolbar.updateActionsImmediately()
    }
    tree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY) { bottomToolbar.updateActionsImmediately() }

    val treePanel = JPanel(BorderLayout())
    treePanel.add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER)

    treeChangesSplitter = TwoKeySplitter(isVertical,
                                         ProportionKey("vcs.saved.patches.changes.splitter.vertical", 0.5f,
                                                       "vcs.saved.patches.changes.splitter.horizontal", 0.5f))
    treeChangesSplitter.firstComponent = treePanel
    treeChangesSplitter.secondComponent = BorderLayoutPanel().apply {
      addToCenter(changesBrowser)
      addToBottom(bottomToolbar.component.apply { border = IdeBorderFactory.createBorder(SideBorder.TOP) })
    }

    treeDiffSplitter = OnePixelSplitter("vcs.saved.patches.diff.splitter", 0.5f)
    treeDiffSplitter.firstComponent = treeChangesSplitter

    updateLayout(isVertical, isEditorDiffPreview, forceDiffPreview = true)

    add(treeDiffSplitter, BorderLayout.CENTER)

    Disposer.register(disposable, this)
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

  fun updateLayout(isVertical: Boolean, canUseEditorDiffPreview: Boolean, forceDiffPreview: Boolean = false) {
    val isEditorDiffPreview = canUseEditorDiffPreview || isVertical
    val isChangesSplitterVertical = isVertical || !isEditorDiffPreview
    if (treeChangesSplitter.orientation != isChangesSplitterVertical) {
      treeChangesSplitter.orientation = isChangesSplitterVertical
    }
    setDiffPreviewInEditor(isEditorDiffPreview, forceDiffPreview)
  }

  private fun setDiffPreviewInEditor(isInEditor: Boolean, force: Boolean = false) {
    if (!force && (isInEditor == (changesBrowser.editorTabPreview != null))) return

    val diffPreviewProcessor = changesBrowser.setDiffPreviewInEditor(isInEditor)
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
    if (SAVED_PATCHES_UI.`is`(dataId)) return this
    return null
  }

  internal fun selectedPatchObjectOrNull() = tree.selectedPatchObjects().findAny().orNull()

  private fun selectedProvider(): SavedPatchesProvider<*> {
    val selectedPatch = selectedPatchObjectOrNull() ?: return providers.first()
    return providers.find { it.dataClass.isInstance(selectedPatch.data) } ?: return providers.first()
  }

  companion object {
    const val SAVED_PATCHES_UI_PLACE = "SavedPatchesUiPlace"
    val SAVED_PATCHES_UI = DataKey.create<SavedPatchesUi>("SavedPatchesUi")
  }
}
