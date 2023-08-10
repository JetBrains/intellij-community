// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ui.branch.dashboard

import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.Wrapper
import org.jetbrains.annotations.NonNls
import java.awt.CardLayout
import javax.swing.JComponent

class ExpandablePanelController(expandedControlContent: JComponent,
                                collapsedControlContent: JComponent,
                                private val expandablePanel: JComponent) {

  @NonNls private val EXPAND = "expand"
  @NonNls private val COLLAPSE = "collapse"

  val expandControlPanel =
    JBPanel<JBPanel<*>>(CardLayout())
      .apply {
        val collapsedWrapped = Wrapper(collapsedControlContent)
        val expandedWrapped = Wrapper(expandedControlContent)
        collapsedWrapped.setHorizontalSizeReferent(expandedWrapped)
        collapsedWrapped.setVerticalSizeReferent(expandedWrapped)
        add(collapsedWrapped, COLLAPSE)
        add(expandedWrapped, EXPAND)
      }

  fun isExpanded(): Boolean = expandablePanel.isVisible

  fun toggleExpand(expand: Boolean) {
    (expandControlPanel.layout as CardLayout).show(expandControlPanel, if (expand) EXPAND else COLLAPSE)
    expandablePanel.isVisible = expand
  }
}