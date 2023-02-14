// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.impl

import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.whenPropertyChanged
import com.intellij.ui.dsl.builder.RowsRange
import com.intellij.ui.layout.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.NonExtendable
internal open class RowsRangeImpl(val panel: PanelImpl, val startIndex: Int) : RowsRange {

  var endIndex = 0
  var visible = true
  var enabled = true

  override fun visible(isVisible: Boolean): RowsRange {
    visible = isVisible
    panel.visibleFromParent(isVisible, startIndex..endIndex)
    return this
  }

  override fun visibleIf(predicate: ComponentPredicate): RowsRange {
    visible(predicate())
    predicate.addListener { visible(it) }
    return this
  }

  override fun visibleIf(property: ObservableProperty<Boolean>): RowsRange {
    visible(property.get())
    property.whenPropertyChanged {
      visible(it)
    }
    return this
  }

  override fun enabled(isEnabled: Boolean): RowsRange {
    enabled = isEnabled
    panel.enabledFromParent(isEnabled, startIndex..endIndex)
    return this
  }

  override fun enabledIf(predicate: ComponentPredicate): RowsRange {
    enabled(predicate())
    predicate.addListener { enabled(it) }
    return this
  }

  override fun enabledIf(property: ObservableProperty<Boolean>): RowsRange {
    enabled(property.get())
    property.whenPropertyChanged {
      enabled(it)
    }
    return this
  }
}
