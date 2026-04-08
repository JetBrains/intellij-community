// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.util.ui.html

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.EDT
import com.intellij.util.ui.JBHtmlEditorKit
import sun.swing.text.GlyphViewAccessor
import java.text.BreakIterator
import javax.swing.text.Element
import javax.swing.text.GlyphView
import javax.swing.text.Segment

// If GlyphViewAccessor is ever removed from the JDK,
// the calcBreakSpots needs to be called from an overridden calcBreakSpots in InlineViewEx.
internal object GlyphViewFix : GlyphViewAccessor() {

  fun init() {
    setAccessor(this)
  }

  private val cache = ContainerUtil.createConcurrentWeakMap<Element, BreakIteratorCache>()

  override fun calcBreakSpots(view: GlyphView, breaker: BreakIterator): IntArray {
    EDT.assertIsEdt()
    val start: Int = view.getStartOffset()
    val end: Int = view.getEndOffset()
    val bs = IntArray(end + 1 - start)
    var ix = 0

    // Breaker should work on the parent element because there may be
    // a valid breakpoint at the end edge of the view (space, etc.)
    val parent: Element? = view.element.parentElement
    val pstart = parent?.startOffset ?: start
    val pend = parent?.endOffset ?: end

    val breaker = getCachedBreakIterator(
      view, parent, pstart, pend, breaker
    )
    breaker.first()

    // Backward search should start from end+1 unless there's NO end+1
    var startFrom = end + (if (pend > end) 1 else 0)
    while (true) {
      startFrom = breaker.preceding(startFrom - pstart) + pstart
      if (startFrom > start) {
        // The break spot is within the view
        bs[ix++] = startFrom
      }
      else {
        break
      }
    }

    val result = IntArray(ix)
    System.arraycopy(bs, 0, result, 0, ix)
    return result
  }

  private fun getCachedBreakIterator(
    view: GlyphView, parent: Element?, pstart: Int, pend: Int,
    commonBreaker: BreakIterator,
  ): BreakIterator {
    val s: Segment = view.getText(pstart, pend)
    val existing = cache[parent]
    val modCount = (view.document as? JBHtmlEditorKit.JBHtmlDocument)?.modCount
    if (existing == null || !existing.isUpToDate(modCount, s)) {
      val str = s.toString()
      val breaker = commonBreaker.clone() as BreakIterator
      breaker.setText(str)
      cache[parent] = if (modCount != null)
        ModCountBreakIteratorCache(modCount, breaker)
      else
        TextBreakIteratorCache(str, breaker)
      return breaker
    }
    else
      return existing.breaker
  }

  private interface BreakIteratorCache {
    val breaker: BreakIterator
    fun isUpToDate(modCount: Long?, s: Segment): Boolean
  }

  private class ModCountBreakIteratorCache(
    private val modCount: Long,
    override val breaker: BreakIterator,
  ) : BreakIteratorCache {

    override fun isUpToDate(modCount: Long?, s: Segment): Boolean {
      return modCount == null || this.modCount == modCount
    }
  }

  private class TextBreakIteratorCache(
    private val text: String,
    override val breaker: BreakIterator,
  ) : BreakIteratorCache {

    override fun isUpToDate(modCount: Long?, s: Segment): Boolean {
      return StringUtil.equals(text, s)
    }
  }


}