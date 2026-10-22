// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import com.intellij.openapi.util.Ref
import java.util.EventListener

/**
 * Gets each change of a property value of an [ObservableState], and each change of an override.
 */
interface ObservableStateListener : EventListener {
  fun propertyChanged(event: PropertyChangeEvent)

  /**
   * The property with [propertyName] got an override, or it lost its override. The value can stay the same, and then
   * [propertyChanged] does not come.
   */
  fun overridingChanged(state: ObservableState, propertyName: String) {}

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
