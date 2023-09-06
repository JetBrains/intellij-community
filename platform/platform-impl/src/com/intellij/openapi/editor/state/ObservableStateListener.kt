// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import com.intellij.openapi.util.Ref
import java.util.EventListener

interface ObservableStateListener : EventListener {

  fun propertyChanged(event: PropertyChangeEvent)

  /**
   * @param oldValueRef `null` if the event emitter decided not to provide old value data
   */
  data class PropertyChangeEvent(val state: ObservableState,
                                 val propertyName: String,
                                 val oldValueRef: Ref<Any?>?,
                                 val newValue: Any?)

}