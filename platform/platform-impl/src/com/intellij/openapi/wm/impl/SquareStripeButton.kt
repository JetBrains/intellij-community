// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import java.awt.Dimension

/**
 * @author Konstantin Bulenkov
 */
open class SquareStripeButton(val button: StripeButton) : StripeButton(button.pane, button.toolWindow) {
  override fun getPreferredSize(): Dimension {
    return Dimension(40, 40)
  }

  override fun updateUI() {
    setUI(SquareStripeButtonUI.createSquareUI(this))
  }
}