// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.listCellRenderer.LcrTextInitParams
import com.intellij.ui.dsl.listCellRenderer.LcrTextSpeedSearchParams
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

@ApiStatus.Internal
class LcrTextInitParamsImpl(foreground: Color) : LcrTextInitParams(foreground) {

  var speedSearchField: LcrTextSpeedSearchParams? = null

  /**
   * The text is used by speed search and therefore should be highlighted while searching
   */
  override fun speedSearch(init: LcrTextSpeedSearchParams.() -> Unit) {
    if (speedSearchField != null) {
      throw UiDslException("SpeedSearch is defined already")
    }

    val speedSearch = LcrTextSpeedSearchParams()
    speedSearch.init()
    this.speedSearchField = speedSearch
  }
}
