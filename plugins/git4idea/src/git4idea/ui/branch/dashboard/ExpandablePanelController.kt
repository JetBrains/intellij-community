// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import org.jetbrains.annotations.NonNls
import java.awt.CardLayout
import java.awt.Component
import javax.swing.JPanel

class ExpandablePanelController(expandedControlContent: Component,
                                collapsedControlContent: Component,
                                private val expandablePanel: Component) {

  @NonNls private val EXPAND = "expand"
  @NonNls private val COLLAPSE = "collapse"

  val expandControlPanel =
    JPanel(CardLayout())
      .apply {
        isOpaque = false
        add(collapsedControlContent, COLLAPSE)
        add(expandedControlContent, EXPAND)
      }

  fun isExpanded(): Boolean = expandablePanel.isVisible

  fun toggleExpand(expand: Boolean) {
    (expandControlPanel.layout as CardLayout).show(expandControlPanel, if (expand) EXPAND else COLLAPSE)
    expandablePanel.isVisible = expand
  }
}