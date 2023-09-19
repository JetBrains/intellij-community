// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.SimpleColoredComponent
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Insets

@ApiStatus.Internal
internal class LcrSimpleColoredTextImpl : LcrCellBaseImpl() {

  override val component = SimpleColoredComponent().apply {
    @Suppress("UseDPIAwareInsets")
    ipad = Insets(ipad.top, 0, ipad.bottom, 0)
    isOpaque = false
  }

  fun init(text: @Nls String, initParams: LcrTextInitParamsImpl) {
    component.clear()
    component.append(text, initParams.attributes!!)
  }
}
