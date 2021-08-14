// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.Label
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JLabel

@DslMarker
private annotation class RootPanelMarker

/**
 * Root panel, does not provide cell related methods
 */
@ApiStatus.Experimental
@RootPanelMarker
interface RootPanel {

  fun row(@Nls label: String, init: Row.() -> Unit): Row {
    return row(Label(label), init)
  }

  fun row(label: JLabel? = null, init: Row.() -> Unit): Row

  /**
   * Adds panel with a title and some space before the panel
   */
  fun group(@NlsContexts.BorderTitle title: String? = null, independent: Boolean = true, init: Panel.() -> Unit): Row
}
