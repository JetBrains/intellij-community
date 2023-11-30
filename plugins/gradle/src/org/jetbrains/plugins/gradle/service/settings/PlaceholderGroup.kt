// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.settings

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.whenPropertyChanged
import com.intellij.ui.dsl.builder.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * TODO(@Pavel Porvatov) replace with visibleId and validation enableIf
 *
 * https://youtrack.jetbrains.com/issue/IDEA-310738/Kotlin-DSL-Cannot-disable-validation-for-invisible-components
 */
@ApiStatus.Obsolete
class PlaceholderGroup<K>(
  private val placeholder: Placeholder
) {

  private val components = HashMap<K, Lazy<JComponent>>()

  fun component(key: K, init: Panel.() -> Unit) {
    components[key] = lazy {
      panel(init = init)
    }
  }

  private fun setSelectedComponent(key: K) {
    placeholder.component = components[key]?.value
  }

  fun bindSelectedComponent(property: ObservableProperty<K>): PlaceholderGroup<K> {
    setSelectedComponent(property.get())
    property.whenPropertyChanged {
      setSelectedComponent(it)
    }
    return this
  }

  companion object {

    /**
     * Creates [Placeholder] and [K] identified components for it.
     * These components can be switched using [PlaceholderGroup].
     */
    @ApiStatus.Obsolete
    fun <K> Row.placeholderGroup(init: PlaceholderGroup<K>.() -> Unit): PlaceholderGroup<K> {
      val placeholder = placeholder()
        .align(AlignX.FILL)
      val result = PlaceholderGroup<K>(placeholder)
      result.init()
      return result
    }
  }
}