// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.builder.impl.checkNull
import com.intellij.ui.dsl.listCellRenderer.LcrTextInitParams
import com.intellij.ui.dsl.listCellRenderer.LcrTextSpeedSearchParams
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Font

@ApiStatus.Internal
class LcrTextInitParamsImpl(foreground: Color, font: Font) : LcrTextInitParams(foreground, font) {

  var speedSearchField: LcrTextSpeedSearchParams? = null

  /**
   * The text is used by speed search and therefore should be highlighted while searching
   */
  override fun speedSearch(init: LcrTextSpeedSearchParams.() -> Unit) {
    checkNull(speedSearchField) { "SpeedSearch is defined already" }

    val speedSearch = LcrTextSpeedSearchParams()
    speedSearch.init()
    this.speedSearchField = speedSearch
  }
}
