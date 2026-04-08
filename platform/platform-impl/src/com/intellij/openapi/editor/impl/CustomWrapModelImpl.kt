// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.diagnostic.Dumpable
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.CustomWrap
import com.intellij.openapi.editor.CustomWrapModel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.impl.customwrap.CustomWrapImpl
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.NonNls

internal class CustomWrapModelImpl(private val editor: EditorImpl) : CustomWrapModel, PrioritizedDocumentListener, Dumpable {
  private val tree: CustomWrapTree = CustomWrapTree(editor.elfDocument)
  private val listeners = ContainerUtil.createLockFreeCopyOnWriteList<CustomWrapModel.Listener>()

  fun addListener(listener: CustomWrapModel.Listener) {
    listeners.add(listener)
  }

  override fun addListener(listener: CustomWrapModel.Listener, disposable: Disposable) {
    listeners.add(listener)
    Disposer.register(disposable) {
      listeners.remove(listener)
    }
  }

  fun removeListener(listener: CustomWrapModel.Listener) {
    listeners.remove(listener)
  }

  private fun notifyAdded(wrap: CustomWrapImpl) {
    for (listener in listeners) {
      listener.customWrapAdded(wrap)
    }
  }

  private fun notifyRemoved(wrap: CustomWrapImpl) {
    for (listener in listeners) {
      listener.customWrapRemoved(wrap)
    }
  }

  internal fun notifyMerged() {
    editor.softWrapModel.customWrapsMerged()
  }

  override fun addWrap(offset: Int, indentInColumns: Int, priority: Int): CustomWrap? {
    val wrap = CustomWrapImpl(offset, editor.document, this, indentInColumns, priority)
    tree.addInterval(wrap, offset, offset, false, false, false, 0)
    notifyAdded(wrap)
    return wrap
  }

  override fun getWraps(): List<CustomWrap> {
    val result = ArrayList<CustomWrapImpl>()
    tree.processAll {
      result.add(it)
      true
    }
    result.sortWith(CUSTOM_WRAP_COMPARATOR)
    return result
  }

  override fun getWrapsInRange(startOffset: Int, endOffset: Int): List<CustomWrapImpl> {
    val result = ArrayList<CustomWrapImpl>()
    tree.processOverlappingWith(startOffset, endOffset) {
      result.add(it)
      true
    }
    result.sortWith(CUSTOM_WRAP_COMPARATOR)
    return result
  }

  override fun getWrapsAtOffset(offset: Int): List<CustomWrapImpl> {
    return getWrapsInRange(offset, offset)
  }

  override fun removeWrap(wrap: CustomWrap) {
    if (tree.removeInterval(wrap as CustomWrapImpl)) {
      notifyRemoved(wrap)
    }
  }

  override fun hasWraps(): Boolean = tree.size() > 0

  private var wrapsAtCaret: List<CustomWrapImpl> = emptyList()

  override fun beforeDocumentChange(event: DocumentEvent) {
    if (editor.document.isInBulkUpdate) return
    // todo check it did not change during bulk op
    val offset = event.offset
    if (event.getOldLength() == 0 && offset == editor.caretModel.offset) {
      // text is being inserted at caret offset
      val wraps = getWrapsAtOffset(offset)
      if (wraps.isNotEmpty()) {
        wrapsAtCaret = wraps
        val caretVisualLine = editor.caretModel.visualPosition.line
        val softWrapVisualLine = editor.offsetToVisualLine(offset, true)
        val shouldStickToRight = caretVisualLine == softWrapVisualLine
        wraps.forEach { it.isStickingToRight = shouldStickToRight }
      }
    }
  }

  override fun documentChanged(event: DocumentEvent) {
    wrapsAtCaret.forEach { it.isStickingToRight = false }
    wrapsAtCaret = emptyList()
  }

  override fun getPriority(): Int {
    return EditorDocumentPriorities.CUSTOM_WRAP_MODEL
  }

  override fun dumpState(): @NonNls String {
    return "${getWraps()}"
  }

  private class CustomWrapTree(document: Document) : HardReferencingRangeMarkerTree<CustomWrapImpl>(document) {
    class Node(
      rangeMarkerTree: RangeMarkerTree<CustomWrapImpl>,
      key: CustomWrapImpl,
      start: Int,
      end: Int,
      stickingToRight: Boolean,
    ) : RMNode<CustomWrapImpl>(rangeMarkerTree, key, start, end, false, false, stickingToRight) {
      override fun addIntervalsFrom(otherNode: IntervalNode<CustomWrapImpl>) {
        super.addIntervalsFrom(otherNode)
        intervals.firstOrNull()?.get()?.model?.notifyMerged()
      }
    }

    public override fun size(): Int {
      return super.size()
    }

    public override fun addInterval(
      interval: CustomWrapImpl,
      start: Int,
      end: Int,
      greedyToLeft: Boolean,
      greedyToRight: Boolean,
      stickingToRight: Boolean,
      layer: Int,
    ): RMNode<CustomWrapImpl> {
      return super.addInterval(interval, start, end, greedyToLeft, greedyToRight, stickingToRight, layer)
    }

    override fun createNewNode(
      key: CustomWrapImpl,
      start: Int,
      end: Int,
      greedyToLeft: Boolean,
      greedyToRight: Boolean,
      stickingToRight: Boolean,
      layer: Int,
    ): RMNode<CustomWrapImpl> {
      return Node(this, key, start, end, stickingToRight)
    }

    override fun fireBeforeRemoved(marker: CustomWrapImpl) {
      marker.model.notifyRemoved(marker)
    }
  }
}

private val CUSTOM_WRAP_COMPARATOR = compareBy<CustomWrapImpl> { it.offset }.thenBy { it.priority }
