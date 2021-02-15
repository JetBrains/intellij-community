// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.ui.DoubleClickListener
import com.intellij.util.Processor
import com.intellij.util.ui.tree.TreeUtil
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.KeyListener
import java.awt.event.MouseEvent
import kotlin.properties.Delegates.observable

private fun ChangesTree.isLeafSelected(): Boolean = lastSelectedPathComponent?.let { model.isLeaf(it) } == true

private class ChangesTreeHandlers(private val tree: ChangesTree) {
  private var enterKeyListener: KeyListener? = null
  private var doubleClickListener: DoubleClickListener? = null

  var enterKeyHandler by observable<Processor<in KeyEvent>?>(null) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable

    if (oldValue == null) enterKeyListener = createEnterListener().also { tree.addKeyListener(it) }
    if (newValue == null) enterKeyListener?.let { tree.removeKeyListener(it) }?.also { enterKeyListener = null }
  }

  var doubleClickHandler by observable<Processor<in MouseEvent>?>(null) { _, oldValue, newValue ->
    if (oldValue == newValue) return@observable

    if (oldValue == null) doubleClickListener = createDoubleClickListener().also { it.installOn(tree) }
    if (newValue == null) doubleClickListener?.uninstall(tree)?.also { doubleClickListener = null }
  }

  private fun createEnterListener(): KeyListener =
    object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent) {
        if (VK_ENTER != e.keyCode || e.modifiers != 0) return
        if (tree.selectionCount <= 1 && !tree.isLeafSelected()) return

        if (enterKeyHandler?.process(e) == true) e.consume()
      }
    }

  private fun createDoubleClickListener(): DoubleClickListener =
    object : DoubleClickListener() {
      override fun onDoubleClick(e: MouseEvent): Boolean {
        val clickPath = TreeUtil.getPathForLocation(tree, e.x, e.y)
        if (clickPath == null) return false

        if (tree.getPathIfCheckBoxClicked(e.point) != null) return false

        return doubleClickHandler?.process(e) == true
      }
    }
}