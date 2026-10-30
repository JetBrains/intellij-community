// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap

import com.intellij.diagnostic.Dumpable
import com.intellij.openapi.editor.CustomWrapModel
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.FoldingListener
import java.beans.PropertyChangeListener

/**
 * Listens to events that may affect soft wraps and triggers recalculations accordingly
 */
internal abstract class SoftWrapRecalculationManager : InlayModel.SimpleAdapter(), DocumentListener, FoldingListener,
                                                       PropertyChangeListener, Dumpable, CustomWrapModel.Listener {
  abstract fun prepareToMapping()

  abstract fun reset()

  abstract fun isResetNeeded(tabWidthChanged: Boolean, fontChanged: Boolean): Boolean

  abstract val isDirty: Boolean

  abstract fun release()

  abstract fun recalculate(reason: String)

  abstract fun dumpName(): String

  override fun toString(): String = dumpState()

  abstract fun onBulkDocumentUpdateStarted()

  abstract fun onBulkDocumentUpdateFinished()

  abstract fun onFontStyleChanged(startOffset: Int, endOffset: Int)

  /**
   * Called when the widths of the text can change in the whole document.
   */
  abstract fun onTextWidthsChanged()

  /**
   * Called when the editor highlighter repaints the text up to [endOffset] during a document change.
   * The editor highlighter lexes the text after the changed text again, and the new tokens can have other font styles.
   * The soft wraps of the document change must then reach [endOffset].
   */
  abstract fun onHighlighterRepaint(endOffset: Int)
}
