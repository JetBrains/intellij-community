// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KProperty

/**
 * The properties of one [ObservableState], in the declaration order, and their positions by name. The first snapshot
 * of the state freezes them, so the layout never changes.
 *
 * All states of one class declare the same properties in the same order, so they share the positions by name.
 */
internal class StateLayout private constructor(
  private val properties: List<StateProperty<Any>>,
  private val names: ClassNames,
) {
  /**
   * All properties, in the declaration order.
   */
  fun properties(): List<StateProperty<Any>> {
    return properties
  }

  /**
   * The property with [name], or `null` when the state has none.
   */
  fun findProperty(name: String): StateProperty<Any>? {
    val index = names.indexOf(name)
    if (index < 0) {
      return null
    }
    return properties[index]
  }

  /**
   * The names of the properties of one state class, and their positions.
   */
  private class ClassNames(private val names: List<String?>) {
    private val indexByName: Map<String, Int> = indexByName(names)

    fun hasNames(otherNames: List<String?>): Boolean {
      return names == otherNames
    }

    /**
     * The position of the first property with [name], or -1 when there is none.
     */
    fun indexOf(name: String): Int {
      return indexByName[name] ?: -1
    }

    private fun indexByName(names: List<String?>): Map<String, Int> {
      val result = HashMap<String, Int>()
      for (index in names.indices) {
        val name = names[index]
        if (name != null) {
          result.putIfAbsent(name, index)
        }
      }
      return java.util.Map.copyOf(result)
    }
  }

  companion object {
    /**
     * The names of each state class. The first state of a class to freeze its properties sets them.
     */
    private val CLASS_NAMES: ClassValue<AtomicReference<ClassNames?>> = object : ClassValue<AtomicReference<ClassNames?>>() {
      override fun computeValue(type: Class<*>): AtomicReference<ClassNames?> {
        return AtomicReference(null)
      }
    }

    /**
     * Freezes the [properties] of [state]. Each state of a class must declare the same properties in the same order.
     */
    fun freeze(state: ObservableState, properties: List<StateProperty<Any>>): StateLayout {
      val names = properties.map { it.name }
      val holder = CLASS_NAMES.get(state.javaClass)
      holder.compareAndSet(null, ClassNames(names))
      val classNames = checkNotNull(holder.get())
      check(classNames.hasNames(names)) {
        "Each state of ${state.javaClass.name} must declare the same properties in the same order"
      }
      return StateLayout(java.util.List.copyOf(properties), classNames)
    }

    /**
     * The position of [property] in each state of [stateClass]. A state of [stateClass] must have frozen its properties
     * already.
     */
    fun indexOf(stateClass: Class<out ObservableState>, property: KProperty<*>): Int {
      val classNames = checkNotNull(CLASS_NAMES.get(stateClass).get()) {
        "No state of ${stateClass.name} has frozen its properties yet"
      }
      val index = classNames.indexOf(property.name)
      require(index >= 0) { "${stateClass.name} has no property ${property.name}" }
      return index
    }
  }
}
