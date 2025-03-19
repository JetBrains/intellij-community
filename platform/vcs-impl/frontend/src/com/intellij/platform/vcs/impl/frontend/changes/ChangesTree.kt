// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider
import com.intellij.openapi.project.Project
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Processor
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ChangesBrowserNode
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ChangesBrowserNodeRenderer
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ChangesBrowserRootNode
import com.intellij.platform.vcs.impl.frontend.shelf.tree.ChangesTreeFrontendCellRenderer
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.MouseEvent
import java.util.*


/*
 * Copy-paste of com.intellij.openapi.vcs.changes.ui.ChangesTree
 */
@ApiStatus.Internal
@Suppress("LeakingThis")
open class ChangesTree(val project: Project, private val cs: CoroutineScope, private val place: String, private val showCheckboxes: Boolean = false, private val highlightProblems: Boolean = false) : Tree(), UiCompatibleDataProvider {
  private val keyHandlers = ChangesTreeHandlers(this)
  private var isModelFlat: Boolean = true
  private val treeExpander = MyTreeExpander()
  protected val groupingSupport: ChangesGroupingSupport = ChangesGroupingSupport(project, place, cs)

  init {
    val nodeRenderer = ChangesBrowserNodeRenderer(project, { false }, false)
    setCellRenderer(ChangesTreeFrontendCellRenderer(nodeRenderer))
    setRootVisible(false)
  }

  fun setDoubleClickHandler(processor: Processor<in MouseEvent>) {
    keyHandlers.doubleClickHandler = processor
  }

  fun setEnterKeyHandler(processor: Processor<in KeyEvent>) {
    keyHandlers.enterKeyHandler = processor
  }

  fun getRoot(): ChangesBrowserRootNode {
    return model.root as ChangesBrowserRootNode
  }

  private fun isCurrentModelFlat(): Boolean {
    var isFlat = true
    val enumeration: Enumeration<*> = getRoot().depthFirstEnumeration()

    while (isFlat && enumeration.hasMoreElements()) {
      isFlat = (enumeration.nextElement() as ChangesBrowserNode<*>).getLevel() <= 1
    }

    return isFlat
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[PlatformDataKeys.TREE_EXPANDER] = treeExpander
    sink[GROUPING_SUPPORT_KEY] = groupingSupport
  }

  inner class MyTreeExpander : DefaultTreeExpander(this) {
    override fun isExpandAllVisible(): Boolean {
      return groupingSupport.isNone() || !isModelFlat
    }

    override fun isCollapseAllVisible(): Boolean {
      return isExpandAllVisible
    }
  }

  companion object {
    val GROUPING_SUPPORT_KEY: DataKey<ChangesGroupingSupport> = DataKey.create<ChangesGroupingSupport>("grouping.support")
  }
}


private class ChangesTreeHandlers(private val tree: ChangesTree) {
  init {
    tree.addKeyListener(MyEnterListener())
    MyDoubleClickListener().installOn(tree)
  }

  var enterKeyHandler: Processor<in KeyEvent>? = null
  var doubleClickHandler: Processor<in MouseEvent>? = null

  private inner class MyEnterListener : KeyAdapter() {
    override fun keyPressed(e: KeyEvent) {
      val handler = enterKeyHandler ?: return

      if (VK_ENTER != e.keyCode || e.modifiers != 0) return
      if (tree.selectionCount <= 1 && !tree.isLeafSelected()) return

      if (handler.process(e)) e.consume()
    }
  }

  private inner class MyDoubleClickListener : DoubleClickListener() {
    override fun onDoubleClick(e: MouseEvent): Boolean {
      val handler = doubleClickHandler ?: return false

      val clickPath = TreeUtil.getPathForLocation(tree, e.x, e.y)
      if (clickPath == null) return false

      return handler.process(e)
    }
  }
}

private fun ChangesTree.isLeafSelected(): Boolean = lastSelectedPathComponent?.let { model.isLeaf(it) } == true