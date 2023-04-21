// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.whenPropertyChanged
import com.intellij.ui.dsl.builder.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.ScheduledForRemoval
@Deprecated("Not needed")
@ApiStatus.Internal
internal class PlaceholderGroupImpl<K>(
  panel: PanelImpl,
  startIndex: Int
) : PlaceholderGroup<K>, RowsRangeImpl(panel, startIndex) {

  private val placeholder: Placeholder

  private val components = HashMap<K, Lazy<JComponent>>()

  override fun component(key: K, init: Panel.() -> Unit) {
    components[key] = lazy {
      panel(init = init)
    }
  }

  override fun setSelectedComponent(key: K) {
    placeholder.component = components[key]?.value
  }

  override fun bindSelectedComponent(property: ObservableProperty<K>): PlaceholderGroup<K> {
    setSelectedComponent(property.get())
    property.whenPropertyChanged {
      setSelectedComponent(it)
    }
    return this
  }

  init {
    placeholder = panel.row {}.placeholder()
      .align(AlignX.FILL)
  }
}