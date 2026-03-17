// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap

import com.intellij.diagnostic.Dumpable
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.InlayModel
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.FoldingListener
import java.beans.PropertyChangeListener

/**
 * Listens to events that may affect soft wraps and triggers recalculations accordingly
 */
internal abstract class SoftWrapRecalculationManager : InlayModel.SimpleAdapter(), DocumentListener, FoldingListener,
                                                       PropertyChangeListener, Dumpable {
  abstract fun prepareToMapping()

  abstract fun reset()

  abstract fun isResetNeeded(tabWidthChanged: Boolean, fontChanged: Boolean): Boolean

  abstract var softWrapPainter: SoftWrapPainter

  abstract fun isDirty(): Boolean

  abstract fun release()

  abstract fun recalculate()

  abstract fun dumpName(): String

  override fun toString(): String = dumpState()
}
