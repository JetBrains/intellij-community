// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.actions.impl.GoToChangePopupBuilder.BaseGoToChangePopupAction
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ui.AsyncChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.PresentableChange
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesTreeModel
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

abstract class PresentableGoToChangePopupAction<T> : BaseGoToChangePopupAction() {
  abstract class Default<T : PresentableChange> : PresentableGoToChangePopupAction<T>() {
    override fun getPresentation(change: T): PresentableChange? = change
  }

  protected abstract fun getChanges(): ListSelection<out T>

  override fun canNavigate(): Boolean = getChanges().getList().size > 1

  protected abstract fun getPresentation(change: T): PresentableChange?

  protected open fun createToolbarActions(): List<AnAction> = listOf()

  protected open fun createPopupMenuActions(): List<AnAction> = listOf()

  private inner class MyAsyncChangesTreeModel(
    private val project: Project,
    private val changes: List<T>,
  ) : SimpleAsyncChangesTreeModel() {
    override fun buildTreeModelSync(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel {
      val groups = MultiMap.createLinked<ChangesBrowserNode.Tag, GenericChangesBrowserNode>()

      for (i in changes.indices) {
        val change = getPresentation(changes[i])
        if (change == null) continue

        val filePath = change.filePath
        val fileStatus = change.fileStatus
        val tag = change.tag
        groups.putValue(tag, GenericChangesBrowserNode(filePath, fileStatus, i))
      }

      val builder = MyTreeModelBuilder(project, grouping)
      for (tag in groups.keySet()) {
        builder.setGenericNodes(groups.get(tag), tag)
      }
      return builder.build()
    }
  }

  protected abstract fun onSelected(change: T)

  override fun createPopup(e: AnActionEvent): JBPopup {
    val project = e.project ?: ProjectManager.getInstance().getDefaultProject()

    val popup = Ref<JBPopup>()
    val cb = MyChangesBrowser(project, popup)

    popup.set(
      JBPopupFactory.getInstance()
        .createComponentPopupBuilder(cb, cb.preferredFocusedComponent)
        .setResizable(true)
        .setModalContext(false)
        .setFocusable(true)
        .setRequestFocus(true)
        .setCancelOnWindowDeactivation(true)
        .setCancelOnOtherWindowOpen(true)
        .setMovable(true)
        .setCancelKeyEnabled(true)
        .setCancelOnClickOutside(true)
        .setDimensionServiceKey(project, "Diff.GoToChangePopup", false)
        .addListener(object : JBPopupListener {
          override fun onClosed(event: LightweightWindowEvent) {
            cb.shutdown()
          }
        })
        .createPopup()
    )

    return popup.get()
  }

  //
  // Helpers
  //
  private inner class MyChangesBrowser(
    project: Project,
    private val popupRef: Ref<JBPopup>,
  ) : AsyncChangesBrowserBase(project, false, false) {
    private val changes = getChanges()

    override val changesTreeModel = MyAsyncChangesTreeModel(myProject, changes.getList())

    init {
      hideViewerBorder()
      myViewer.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION)
      init()

      val viewer = getViewer()
      viewer.requestRefresh()

      if (changes.selectedIndex != -1) {
        DiffUtil.runWhenFirstShown(this, Runnable {
          viewer.invokeAfterRefresh(Runnable {
            val toSelect = TreeUtil.findNode(myViewer.root, Condition { node: DefaultMutableTreeNode? ->
              node is GenericChangesBrowserNode &&
              node.index == changes.selectedIndex
            })
            if (toSelect != null) {
              TreeUtil.selectNode(myViewer, toSelect)
            }
          })
        })
      }
    }

    override fun createToolbarActions(): List<AnAction> {
      return this@PresentableGoToChangePopupAction.createToolbarActions()
    }

    override fun createPopupMenuActions(): List<AnAction> {
      return this@PresentableGoToChangePopupAction.createPopupMenuActions()
    }

    override fun onDoubleClick() {
      popupRef.get().cancel()

      val selection = VcsTreeModelData.selected(myViewer).iterateNodes().first()
      val node = (selection as? GenericChangesBrowserNode) ?: return

      val newSelection = changes.getList()[node.index]
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(Runnable { onSelected(newSelection) })
    }
  }

  private class GenericChangesBrowserNode(
    val filePath: FilePath,
    val fileStatus: FileStatus,
    val index: Int,
  ) : ChangesBrowserNode<FilePath>(filePath), Comparable<GenericChangesBrowserNode> {
    override fun isFile(): Boolean = !isDirectory()

    override fun isDirectory(): Boolean = filePath.isDirectory()

    override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
      renderer.appendFileName(filePath.getVirtualFile(), filePath.getName(), fileStatus.getColor())

      if (renderer.isShowFlatten) {
        appendParentPath(renderer, filePath.getParentPath())
      }

      if (!renderer.isShowFlatten && getFileCount() != 1 || getDirectoryCount() != 0) {
        appendCount(renderer)
      }

      renderer.setIcon(this.filePath, filePath.isDirectory() || !isLeaf)
    }

    override fun getTextPresentation(): String = filePath.getName()

    override fun toString(): String = FileUtil.toSystemDependentName(filePath.getPath())

    override fun compareTo(other: GenericChangesBrowserNode): Int = compareFilePaths(this.filePath, other.filePath)
  }

  private class MyTreeModelBuilder(project: Project, grouping: ChangesGroupingPolicyFactory) : TreeModelBuilder(project, grouping) {
    fun setGenericNodes(nodes: Collection<GenericChangesBrowserNode>, tag: ChangesBrowserNode.Tag?) {
      val parentNode = createTagNode(tag)

      for (node in ContainerUtil.sorted(nodes, Comparator.comparing({ it.filePath }, PATH_COMPARATOR))) {
        insertChangeNode(node.filePath, parentNode, node)
      }
    }
  }
}
