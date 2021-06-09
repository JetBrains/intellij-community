// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager
import com.intellij.openapi.vcs.changes.EditorTabPreview
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class GitStashUi(private val project: Project, isEditorDiffPreview: Boolean, disposable: Disposable) :
  JPanel(BorderLayout()), Disposable, DataProvider {

  private val tree: ChangesTree
  private val treeDiffSplitter: OnePixelSplitter
  private val toolbar: JComponent

  private var diffPreviewProcessor: GitStashDiffPreview? = null
  private var editorTabPreview: EditorTabPreview? = null

  init {
    tree = GitStashTree(project, this)
    PopupHandler.installPopupMenu(tree, "Git.Stash.ContextMenu", GIT_STASH_UI_PLACE)

    toolbar = buildToolbar()

    val treePanel = JPanel(BorderLayout())
    treePanel.add(toolbar, BorderLayout.NORTH)
    treePanel.add(ScrollPaneFactory.createScrollPane(tree, SideBorder.TOP), BorderLayout.CENTER)

    treeDiffSplitter = OnePixelSplitter("git.stash.diff.splitter", 0.5f)
    treeDiffSplitter.firstComponent = treePanel
    setDiffPreviewInEditor(isEditorDiffPreview, force = true)

    add(treeDiffSplitter, BorderLayout.CENTER)

    Disposer.register(disposable, this)
  }

  private fun buildToolbar(): JComponent {
    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.add(ActionManager.getInstance().getAction("Git.Stash.Toolbar"))
    toolbarGroup.addSeparator()
    toolbarGroup.add(ActionManager.getInstance().getAction(ChangesTree.GROUP_BY_ACTION_GROUP))
    toolbarGroup.addSeparator()
    toolbarGroup.addAll(TreeActionsToolbarPanel.createTreeActions(tree))
    val toolbar = ActionManager.getInstance().createActionToolbar(GIT_STASH_UI_PLACE, toolbarGroup, true)
    toolbar.setTargetComponent(tree)
    return toolbar.component
  }

  fun setDiffPreviewInEditor(isInEditor: Boolean, force: Boolean = false) {
    if (!force && (isInEditor == (editorTabPreview != null))) return

    if (diffPreviewProcessor != null) Disposer.dispose(diffPreviewProcessor!!)
    diffPreviewProcessor = GitStashDiffPreview(project, tree, isInEditor, this)
    diffPreviewProcessor!!.toolbarWrapper.setVerticalSizeReferent(toolbar)

    if (isInEditor) {
      editorTabPreview = GitStashEditorDiffPreview(diffPreviewProcessor!!, tree, this)
      treeDiffSplitter.secondComponent = null
    }
    else {
      editorTabPreview = null
      treeDiffSplitter.secondComponent = diffPreviewProcessor!!.component
    }
  }

  override fun dispose() {
  }

  override fun getData(dataId: String): Any? {
    if (EditorTabDiffPreviewManager.EDITOR_TAB_DIFF_PREVIEW.`is`(dataId)) return editorTabPreview

    return null
  }

  companion object {
    const val GIT_STASH_UI_PLACE = "GitStashUiPlace"
  }
}
