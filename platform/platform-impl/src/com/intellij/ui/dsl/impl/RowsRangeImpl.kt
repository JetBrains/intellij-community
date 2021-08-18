// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.ui.dsl.RowsRange
import com.intellij.ui.layout.*

internal class RowsRangeImpl(val panel: PanelImpl, val startIndex: Int) : RowsRange {

  var endIndex = 0

  override fun visible(isVisible: Boolean): RowsRange {
    panel.visible(isVisible, startIndex..endIndex)
    return this
  }

  override fun enabled(isEnabled: Boolean): RowsRange {
    panel.enabled(isEnabled, startIndex..endIndex)
    return this
  }

  override fun enabledIf(predicate: ComponentPredicate): RowsRange {
    enabled(predicate())
    predicate.addListener { enabled(it) }
    return this
  }
}