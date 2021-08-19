// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.impl

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import javax.swing.JComponent

internal const val DSL_LABEL_NO_BOTTOM_GAP_PROPERTY = "dsl.label.no.bottom.gap"

internal val JComponent.origin: JComponent
  get() {
    return when (this) {
      is TextFieldWithBrowseButton -> textField
      else -> this
    }
  }
