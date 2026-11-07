// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

/**
 * The changes and the events of an open [ObservableState.batch]. Only the thread of the batch uses it.
 *
 * The batch starts from the values and the overrides of [base], and [base] stays unchanged. When the outermost batch
 * ends, the state makes its next snapshot from the batch, and then it gives the events to the listeners.
 */
internal class StateBatch(base: StateSnapshot) {
  private var values: Array<Any?> = base.values()
  private var overrides: Array<Any?> = base.overrides()
  private var valuesCopied: Boolean = false
  private var overridesCopied: Boolean = false
  private val events: MutableList<() -> Unit> = ArrayList()
  private val valueEvents: MutableList<ObservableStateListener.PropertyChangeEvent> = ArrayList()

  fun valueAt(index: Int): Any? {
    return values[index]
  }

  /**
   * The override at [index], or [NoOverride].
   */
  fun overrideAt(index: Int): Any? {
    return overrides[index]
  }

  fun storeValue(index: Int, value: Any?) {
    if (!valuesCopied) {
      values = values.copyOf()
      valuesCopied = true
    }
    values[index] = value
  }

  /**
   * Stores [override] at [index]. [NoOverride] removes the override.
   */
  fun storeOverride(index: Int, override: Any?) {
    if (!overridesCopied) {
      overrides = overrides.copyOf()
      overridesCopied = true
    }
    overrides[index] = override
  }

  fun addEvent(event: () -> Unit) {
    events.add(event)
  }

  /**
   * Adds the value change [event], which the listeners also get in one list when the batch ends.
   */
  fun addValueEvent(event: ObservableStateListener.PropertyChangeEvent) {
    valueEvents.add(event)
  }

  /**
   * Whether the batch changed a value or an override. A change that a later change undoes still counts.
   */
  fun hasChanges(): Boolean {
    return valuesCopied || overridesCopied
  }

  fun values(): Array<Any?> {
    return values
  }

  fun overrides(): Array<Any?> {
    return overrides
  }

  /**
   * The events of the batch, in their order.
   */
  fun events(): List<() -> Unit> {
    return events
  }

  /**
   * The value change events of the batch, in their order.
   */
  fun valueEvents(): List<ObservableStateListener.PropertyChangeEvent> {
    return valueEvents
  }
}
