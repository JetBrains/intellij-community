// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.listCellRenderer.LcrTextInitParams
import org.jetbrains.annotations.ApiStatus
import java.awt.Color

@ApiStatus.Internal
internal class LcrTextInitParamsImpl(override var foreground: Color): LcrInitParamsImpl(), LcrTextInitParams {

  override var attributes: SimpleTextAttributes? = null

  fun isSimpleText(): Boolean {
    return attributes == null
  }
}