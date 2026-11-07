// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import com.intellij.openapi.util.Ref
import java.util.EventListener

/**
 * Gets each change of a property value of an [ObservableState], and each change of an override.
 *
 * An event comes on the thread that made the change, when [ObservableState.getSnapshot] already shows the change. The
 * listener runs without a lock of the state, so it can change the state again. A batch gives its events after it ends,
 * in their order.
 *
 * The events of changes on two threads can come in another order than the changes. So a listener that needs the
 * current value reads it from the state, and not from the event.
 */
interface ObservableStateListener : EventListener {
  /**
   * The property with [PropertyChangeEvent.propertyName] got a new value.
   */
  fun propertyChanged(event: PropertyChangeEvent)

  /**
   * The property with [propertyName] got an override, or it lost its override. The value can stay the same, and then
   * [propertyChanged] does not come.
   */
  fun overridingChanged(state: ObservableState, propertyName: String) {}

  /**
   * All value changes of one batch, in their order. They come once after the [propertyChanged] events of the batch, and
   * only when the batch changed a value. So a listener can react once to several changes.
   */
  fun propertiesChanged(events: List<PropertyChangeEvent>) {}

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
