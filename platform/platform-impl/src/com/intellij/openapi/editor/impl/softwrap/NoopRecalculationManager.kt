// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.softwrap

import org.jetbrains.annotations.NonNls
import java.beans.PropertyChangeEvent

internal object NoopRecalculationManager : SoftWrapRecalculationManager() {
  override fun prepareToMapping() {}

  override fun propertyChange(evt: PropertyChangeEvent?) {}

  override fun dumpState(): @NonNls String = toString()

  override fun reset() {}

  override fun isResetNeeded(tabWidthChanged: Boolean, fontChanged: Boolean): Boolean {
    return false
  }

  override var softWrapPainter: SoftWrapPainter
    get() = EmptySoftWrapPainter
    set(_) {}

  override fun isDirty(): Boolean {
    return false
  }

  override fun release() {}

  override fun recalculate() {}

  override fun dumpName() = "noop"
}
