// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ui.DoubleClickListener
import com.intellij.util.Processor
import com.intellij.util.ui.tree.TreeUtil
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.MouseEvent

private fun ChangesTree.isLeafSelected(): Boolean = lastSelectedPathComponent?.let { model.isLeaf(it) } == true

internal class ChangesTreeHandlers(private val tree: ChangesTree) {
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

      if (tree.getPathIfCheckBoxClicked(e.point) != null) return false

      return handler.process(e)
    }
  }
}