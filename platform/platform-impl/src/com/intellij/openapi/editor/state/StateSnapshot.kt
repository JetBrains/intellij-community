// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

/**
 * The values and the overrides of all properties of an [ObservableState] at one moment. [ObservableState.getSnapshot]
 * gives the current snapshot.
 *
 * A snapshot never changes, and any thread can read it without a lock. Each change of the state gives a new snapshot.
 * The snapshot keeps only the references to the values, so a mutable value object can still change.
 *
 * A subclass can add typed accessors. The state creates it in [ObservableState.createSnapshot].
 */
open class StateSnapshot internal constructor(
  private val layout: StateLayout,
  private val values: Array<Any?>,
  private val overrides: Array<Any?>,
) {
  /**
   * The value of [property]. The property must belong to the state that gave this snapshot.
   */
  fun <T> get(property: StateProperty<T>): T {
    // Safe: only this property writes the entry at its index, and its initial value, its default and its override are T.
    @Suppress("UNCHECKED_CAST")
    return values[property.index] as T
  }

  /**
   * Whether [property] has an override. The property must belong to the state that gave this snapshot.
   */
  fun isOverridden(property: StateProperty<*>): Boolean {
    return isOverriddenAt(property.index)
  }

  /**
   * The properties of the state, frozen at its first snapshot. All snapshots of the state share it.
   */
  internal fun layout(): StateLayout {
    return layout
  }

  internal fun valueAt(index: Int): Any? {
    return values[index]
  }

  internal fun isOverriddenAt(index: Int): Boolean {
    return overrides[index] !== NoOverride
  }

  /**
   * The override at [index], or [NoOverride].
   */
  internal fun overrideAt(index: Int): Any? {
    return overrides[index]
  }

  /**
   * The values array itself. A caller must not change it.
   */
  internal fun values(): Array<Any?> {
    return values
  }

  /**
   * The overrides array itself. A caller must not change it.
   */
  internal fun overrides(): Array<Any?> {
    return overrides
  }
}

/**
 * The entry of a property without an override in the overrides of a [StateSnapshot]. A `null` entry is an override with
 * the value `null`.
 */
internal data object NoOverride
