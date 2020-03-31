// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcsUtil

import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent

object UIVcsUtilKt {
  @JvmStatic
  fun JComponent.installVisibilityReferent(referent: JComponent) {
    referent.addComponentListener(object: ComponentAdapter() {
      override fun componentHidden(e: ComponentEvent) = toggleVisibility(e)
      override fun componentShown(e: ComponentEvent) = toggleVisibility(e)

      private fun toggleVisibility(e: ComponentEvent) {
        val component = e.component ?: return
        isVisible = component.isVisible
      }
    })
  }
}
