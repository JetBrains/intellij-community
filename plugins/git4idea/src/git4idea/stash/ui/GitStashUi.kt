// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.util.containers.orNull
import git4idea.index.ui.ProportionKey
import git4idea.index.ui.TwoKeySplitter
import git4idea.ui.StashInfo
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class GitStashUi(project: Project, isVertical: Boolean, isEditorDiffPreview: Boolean, disposable: Disposable) :
  JPanel(BorderLayout()), Disposable, DataProvider {

  private val tree: ChangesTree
  internal val changesBrowser: GitStashChangesBrowser
  private val treeChangesSplitter: TwoKeySplitter
  private val treeDiffSplitter: OnePixelSplitter
  private val toolbar: JComponent

  init {
    tree = GitStashTree(project, this)
    PopupHandler.installPopupMenu(tree, "Git.Stash.ContextMenu", GIT_STASH_UI_PLACE)

    changesBrowser = GitStashChangesBrowser(project, this)
    tree.addSelectionListener {
      changesBrowser.selectStash(VcsTreeModelData.selected(tree).userObjectsStream(StashInfo::class.java).findAny().orNull())
    }

    toolbar = buildToolbar()

    val treePanel = JPanel(BorderLayout())
    treePanel.add(toolbar, BorderLayout.NORTH)
    treePanel.add(ScrollPaneFactory.createScrollPane(tree, SideBorder.TOP), BorderLayout.CENTER)

    treeChangesSplitter = TwoKeySplitter(isVertical,
                                         ProportionKey("git.stash.changes.splitter.vertical", 0.5f,
                                                       "git.stash.changes.splitter.horizontal", 0.5f))
    treeChangesSplitter.firstComponent = treePanel
    treeChangesSplitter.secondComponent = changesBrowser

    treeDiffSplitter = OnePixelSplitter("git.stash.diff.splitter", 0.5f)
    treeDiffSplitter.firstComponent = treeChangesSplitter

    updateLayout(isVertical, isEditorDiffPreview, forceDiffPreview = true)

    add(treeDiffSplitter, BorderLayout.CENTER)

    Disposer.register(disposable, this)
  }

  private fun buildToolbar(): JComponent {
    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.add(ActionManager.getInstance().getAction("Git.Stash.Toolbar"))
    toolbarGroup.addSeparator()
    toolbarGroup.addAll(TreeActionsToolbarPanel.createTreeActions(tree))
    val toolbar = ActionManager.getInstance().createActionToolbar(GIT_STASH_UI_PLACE, toolbarGroup, true)
    toolbar.targetComponent = tree
    return toolbar.component
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
    diffPreviewProcessor.toolbarWrapper.setVerticalSizeReferent(toolbar)
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
    if (GIT_STASH_UI.`is`(dataId)) return this
    return null
  }

  companion object {
    const val GIT_STASH_UI_PLACE = "GitStashUiPlace"
    val GIT_STASH_UI = DataKey.create<GitStashUi>("GitStashUi")
  }
}
