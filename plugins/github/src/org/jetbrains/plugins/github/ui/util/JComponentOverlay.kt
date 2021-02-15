// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.util

import com.intellij.util.ui.SingleComponentCenteringLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLayeredPane

object JComponentOverlay {

  fun createCentered(component: JComponent, centeredOverlay: JComponent): JLayeredPane {
    val pane = object : JLayeredPane() {
      override fun getPreferredSize(): Dimension = component.preferredSize

      override fun doLayout() {
        super.doLayout()
        component.setBounds(0, 0, width, height)
        centeredOverlay.bounds = SingleComponentCenteringLayout.getBoundsForCentered(component, centeredOverlay)
      }
    }
    pane.isFocusable = false
    pane.add(component, JLayeredPane.DEFAULT_LAYER, 0)
    pane.add(centeredOverlay, JLayeredPane.POPUP_LAYER, 1)
    return pane
  }
}
