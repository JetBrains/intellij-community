// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.diagnostic.Dumpable
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.CustomWrap
import com.intellij.openapi.editor.CustomWrapModel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.ElfCandidate
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener
import com.intellij.openapi.editor.ex.RangeMarkerEx
import com.intellij.openapi.editor.impl.customwrap.CustomWrapImpl
import com.intellij.util.DocumentUtil
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly

@ElfCandidate
internal class CustomWrapModelImpl(private val editor: EditorImpl) : CustomWrapModel, CustomWrapModel.Mutator, PrioritizedDocumentListener,
                                                                     Dumpable, Disposable {
  private val document = editor.elfDocument
  private val tree: CustomWrapTree = CustomWrapTree(document)
  @Volatile
  private var snapshotStorage: SnapshotCustomWrapStorage? = null
  private val eventDispatcher = EventDispatcher.create(CustomWrapModel.Listener::class.java)
  private var isInsideBatchMutation = false

  fun addListener(listener: CustomWrapModel.Listener) {
    eventDispatcher.addListener(listener)
  }

  override fun addListener(listener: CustomWrapModel.Listener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }

  fun removeListener(listener: CustomWrapModel.Listener) {
    eventDispatcher.removeListener(listener)
  }

  private fun notifyAdded(wrap: CustomWrap) {
    eventDispatcher.multicaster.customWrapAdded(wrap)
  }

  internal fun notifyRemoved(wrap: CustomWrap) {
    eventDispatcher.multicaster.customWrapRemoved(wrap)
  }

  private fun notifyBatchStart() {
    eventDispatcher.multicaster.customWrapBatchMutationStarted()
  }

  private fun notifyBatchFinish() {
    eventDispatcher.multicaster.customWrapBatchMutationFinished()
  }

  override fun addWrap(offset: Int, indentInColumns: Int, priority: Int): CustomWrap? {
    require(isInsideBatchMutation) { "#addWrap must be called inside #runBatchMutation" }
    val document = document
    if (offset < 0 || offset > document.textLength) return null
    if (!isValidCustomWrapOffset(offset, document)) {
      return null
    }
    val wrap = if (RangeMarkerStorageImpl.Holder.USE_PMARKER_IMPLEMENTATION) {
      getOrCreateSnapshotStorage().create(offset, indentInColumns, priority)
    }
    else {
      CustomWrapImpl(offset, document, this, indentInColumns, priority).also {
        tree.addInterval(it, offset, offset, greedyToLeft = false, greedyToRight = false, stickingToRight = false, layer = 0)
      }
    }
    notifyAdded(wrap)
    return wrap
  }

  override fun getWraps(): List<CustomWrap> {
    val result = ArrayList<CustomWrap>()
    tree.processAll {
      result.add(it)
      true
    }
    snapshotStorage?.collectAll()?.let(result::addAll)
    result.sortWith(CUSTOM_WRAP_COMPARATOR)
    return result
  }

  override fun getWrapsInRange(startOffset: Int, endOffset: Int): List<CustomWrap> {
    val result = ArrayList<CustomWrap>()
    tree.processOverlappingWith(startOffset, endOffset) {
      result.add(it)
      true
    }
    snapshotStorage?.collectInRange(startOffset, endOffset)?.let(result::addAll)
    result.sortWith(CUSTOM_WRAP_COMPARATOR)
    return result
  }

  override fun getWrapsAtOffset(offset: Int): List<CustomWrap> {
    return getWrapsInRange(offset, offset)
  }

  override fun removeWrap(wrap: CustomWrap): Boolean {
    require(isInsideBatchMutation) { "#removeWrap must be called inside #runBatchMutation" }
    if (wrap is SnapshotCustomWrap) {
      return snapshotStorage?.remove(wrap) == true
    }
    return tree.removeInterval(wrap as CustomWrapImpl)
  }

  override fun hasWraps(): Boolean = tree.size() > 0 || snapshotStorage?.hasWraps() == true

  override fun <T> runBatchMutation(mutation: CustomWrapModel.Mutator.() -> T): T {
    if (isInsideBatchMutation) {
      return mutation(this)
    }

    val result = try {
      isInsideBatchMutation = true
      notifyBatchStart()
      mutation(this)
    }
    finally {
      isInsideBatchMutation = false
      notifyBatchFinish()

      if (!document.isInBulkUpdate) {
        editor.updateCaretCursor()
        editor.recalculateSizeAndRepaint()
        (editor.gutterComponentEx as EditorGutterComponentImpl).updateSize()
        editor.gutterComponentEx.repaint()
        editor.invokeDelayedErrorStripeRepaint()
      }
    }
    return result
  }

  private var wrapsAtCaret: List<CustomWrap> = emptyList()

  override fun beforeDocumentChange(event: DocumentEvent) {
    if (document.isInBulkUpdate) return
    LOG.assertTrue(!isInsideBatchMutation, "Document must not be modified inside batch mutation")
    val offset = event.offset
    if (event.getOldLength() == 0 && offset == editor.caretModel.offset) {
      // text is being inserted at caret offset
      val wraps = getWrapsAtOffset(offset)
      if (wraps.isNotEmpty()) {
        wrapsAtCaret = wraps
        val caretVisualLine = editor.caretModel.visualPosition.line
        val softWrapVisualLine = editor.offsetToVisualLine(offset, true)
        val shouldStickToRight = caretVisualLine == softWrapVisualLine
        wraps.forEach { (it as RangeMarkerEx).setStickingToRight(shouldStickToRight) }
      }
    }
  }

  override fun documentChanged(event: DocumentEvent) {
    wrapsAtCaret.forEach { (it as RangeMarkerEx).setStickingToRight(false) }
    wrapsAtCaret = emptyList()
  }

  override fun getPriority(): Int {
    return EditorDocumentPriorities.CUSTOM_WRAP_MODEL
  }

  override fun dumpState(): @NonNls String {
    return "${getWraps()}"
  }
  
  @TestOnly
  fun validateState() {
    val customWraps = getWraps()
    for (wrap in customWraps) {
      LOG.assertTrue(isValidCustomWrapOffset(wrap.offset, document))
    }
    if (document.isInBulkUpdate) {
      return
    }
    val notCollapsedUniqueWraps: List<CustomWrap> = customWraps
      // only not collapsed
      .filter {
        val foldRegion = editor.foldingModel.getCollapsedRegionAtOffset(it.offset)
        foldRegion == null || it.offset == foldRegion.startOffset
      }
      // only one wrap per offset
      .fold(mutableListOf()) { acc, wrap ->
        val prev = acc.lastOrNull()
        if (prev == null || prev.offset < wrap.offset) {
          acc.add(wrap)
        }
        acc
      }
    val softWraps = editor.softWrapModel.getRegisteredSoftWrapsEx()
    val customSoftWraps = softWraps.filter { it.isCustomSoftWrap }
    LOG.assertTrue(notCollapsedUniqueWraps.size == customSoftWraps.size)
  }

  override fun dispose() {
    tree.dispose(document)
    snapshotStorage?.dispose()
  }

  @Synchronized
  private fun getOrCreateSnapshotStorage(): SnapshotCustomWrapStorage {
    var storage = snapshotStorage
    if (storage == null) {
      storage = SnapshotCustomWrapStorage(this, document as DocumentImpl)
      snapshotStorage = storage
    }
    return storage
  }

  private inner class CustomWrapTree(document: Document) : HardReferencingRangeMarkerTree<CustomWrapImpl>(document) {
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

    override fun fireAfterRemoved(marker: CustomWrapImpl) {
      notifyRemoved(marker)
    }

    public override fun dispose(document: Document) {
      super.dispose(document)
    }
  }
}

private val LOG = logger<CustomWrapModelImpl>()

private val CUSTOM_WRAP_COMPARATOR = compareBy<CustomWrap> { it.offset }.thenBy { it.priority }

@ApiStatus.Internal
fun isValidCustomWrapOffset(offset: Int, document: Document): Boolean =
  !DocumentUtil.isInsideCharacterPair(document, offset)
  && !DocumentUtil.isAtLineStart(offset, document)
  && !DocumentUtil.isAtLineEnd(offset, document)
