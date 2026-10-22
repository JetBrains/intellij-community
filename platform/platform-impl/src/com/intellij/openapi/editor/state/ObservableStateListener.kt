// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import com.intellij.openapi.util.Ref
import java.util.EventListener

/**
 * Gets each change of a property value of an [ObservableState].
 */
interface ObservableStateListener : EventListener {
  fun propertyChanged(event: PropertyChangeEvent)

  /**
   * @param oldValueRef the old value, or `null` when the sender gives none
   */
  data class PropertyChangeEvent(
    val state: ObservableState,
    val propertyName: String,
    val oldValueRef: Ref<Any?>?,
    val newValue: Any?,
  )
}
