// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.actions.diff

import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.ListSelection
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
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
import org.jetbrains.annotations.ApiStatus
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

@ApiStatus.Internal
interface GoToChangePopupController<T : Any> {
  fun getPresentation(change: T): PresentableChange?

  fun createToolbarActions(): List<AnAction> = listOf()
  fun createPopupMenuActions(): List<AnAction> = listOf()

  fun onSelected(change: T)
}

@ApiStatus.Internal
object GoToChangePopupUtil {
  fun <T : Any> createPopup(
    project: Project,
    changes: ListSelection<out T>,
    controller: GoToChangePopupController<T>,
  ): JBPopup {
    val popupRef = Ref<JBPopup>()
    val closingController = object : GoToChangePopupController<T> by controller {
      override fun onSelected(change: T) {
        popupRef.get()?.cancel()
        controller.onSelected(change)
      }
    }
    val cb = MyChangesBrowser(project, changes, closingController)
    val popup = JBPopupFactory.getInstance()
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
      .createPopup().also {
        popupRef.set(it)
      }
    return popup
  }

  //
  // Helpers
  //
  private class MyAsyncChangesTreeModel<T : Any>(
    private val project: Project,
    private val changes: List<T>,
    private val controller: GoToChangePopupController<T>,
  ) : SimpleAsyncChangesTreeModel() {
    override fun buildTreeModelSync(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel {
      val groups = MultiMap.createLinked<ChangesBrowserNode.Tag, GenericChangesBrowserNode>()

      for (i in changes.indices) {
        val change = controller.getPresentation(changes[i])
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

  private class MyChangesBrowser<T : Any>(
    project: Project,
    private val changes: ListSelection<out T>,
    private val controller: GoToChangePopupController<T>,
  ) : AsyncChangesBrowserBase(project, false, false) {

    override val changesTreeModel = MyAsyncChangesTreeModel(myProject, changes.getList(), controller)

    init {
      hideViewerBorder()
      myViewer.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION)
      init()

      val viewer = getViewer()
      viewer.requestRefresh()

      if (changes.selectedIndex != -1) {
        DiffUtil.runWhenFirstShown(this, Runnable {
          viewer.invokeAfterRefresh(Runnable {
            val toSelect = TreeUtil.findNode(myViewer.getRoot(), Condition { node: DefaultMutableTreeNode? ->
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

    override fun createToolbarActions(): List<AnAction> = controller.createToolbarActions()
    override fun createPopupMenuActions(): List<AnAction> = controller.createPopupMenuActions()

    override fun onDoubleClick() {
      val selection = VcsTreeModelData.selected(myViewer).iterateNodes().first()
      val node = (selection as? GenericChangesBrowserNode) ?: return

      val newSelection = changes.getList()[node.index]
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(Runnable { controller.onSelected(newSelection) })
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

      renderer.setIcon(this.filePath, filePath.isDirectory() || !isLeaf())
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