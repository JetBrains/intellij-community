// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.*
import com.intellij.util.containers.orNull
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.commit.CommitActionsPanel
import git4idea.index.ui.ProportionKey
import git4idea.index.ui.TwoKeySplitter
import git4idea.ui.StashInfo
import java.awt.BorderLayout
import javax.swing.JPanel

class GitStashUi(project: Project, isVertical: Boolean, isEditorDiffPreview: Boolean, disposable: Disposable) :
  JPanel(BorderLayout()), Disposable, DataProvider {

  private val tree: ChangesTree
  internal val changesBrowser: GitStashChangesBrowser
  private val treeChangesSplitter: TwoKeySplitter
  private val treeDiffSplitter: OnePixelSplitter

  init {
    tree = GitStashTree(project, this)
    PopupHandler.installPopupMenu(tree, "Git.Stash.ContextMenu", GIT_STASH_UI_PLACE)

    changesBrowser = GitStashChangesBrowser(project, this)
    val bottomToolbar = buildBottomToolbar()

    tree.addSelectionListener {
      changesBrowser.selectStash(VcsTreeModelData.selected(tree).userObjectsStream(StashInfo::class.java).findAny().orNull())
      bottomToolbar.updateActionsImmediately()
    }

    val treePanel = JPanel(BorderLayout())
    treePanel.add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER)

    treeChangesSplitter = TwoKeySplitter(isVertical,
                                         ProportionKey("git.stash.changes.splitter.vertical", 0.5f,
                                                       "git.stash.changes.splitter.horizontal", 0.5f))
    treeChangesSplitter.firstComponent = treePanel
    treeChangesSplitter.secondComponent = BorderLayoutPanel().apply {
      addToCenter(changesBrowser)
      addToBottom(bottomToolbar.component.apply { border = IdeBorderFactory.createBorder(SideBorder.TOP) })
    }

    treeDiffSplitter = OnePixelSplitter("git.stash.diff.splitter", 0.5f)
    treeDiffSplitter.firstComponent = treeChangesSplitter

    updateLayout(isVertical, isEditorDiffPreview, forceDiffPreview = true)

    add(treeDiffSplitter, BorderLayout.CENTER)

    Disposer.register(disposable, this)
  }

  private fun buildBottomToolbar(): ActionToolbar {
    val actions = listOfNotNull(
      ActionManager.getInstance().getAction(GIT_STASH_APPLY_ACTION),
      ActionManager.getInstance().getAction(GIT_STASH_POP_ACTION)
    )

    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.addAll(actions.withIndex().map { (index, action) ->
      val isDefault = index == 0
      JButtonActionWrapper(action, isDefault).apply {
        if (isDefault) {
          registerCustomShortcutSet(CommitActionsPanel.DEFAULT_COMMIT_ACTION_SHORTCUT, this@GitStashUi, this@GitStashUi)
        }
      }
    })
    val toolbar = ActionManager.getInstance().createActionToolbar(GIT_STASH_UI_PLACE, toolbarGroup, true)
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
    if (GIT_STASH_UI.`is`(dataId)) return this
    return null
  }

  companion object {
    private const val GIT_STASH_APPLY_ACTION = "Git.Stash.Apply"
    private const val GIT_STASH_POP_ACTION = "Git.Stash.Pop"

    const val GIT_STASH_UI_PLACE = "GitStashUiPlace"
    val GIT_STASH_UI = DataKey.create<GitStashUi>("GitStashUi")
  }
}
