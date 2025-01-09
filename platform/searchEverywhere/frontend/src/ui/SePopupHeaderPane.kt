// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.tabbedPaneHeader
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.bindSelectedTabIn
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

@Internal
class SePopupHeaderPane(tabNames: @Nls List<String>,
                        selectedTabState: MutableStateFlow<Int>,
                        coroutineScope: CoroutineScope,
                        toolbar: JComponent? = null): NonOpaquePanel() {
  private lateinit var tabbedPane: JBTabbedPane
  private val panel: DialogPanel

  init {
    background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND

    panel = panel {
      row {
        tabbedPane = tabbedPaneHeader()
          .customize(UnscaledGaps.EMPTY)
          .applyToComponent {
            font = JBFont.regular()
            background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND
            isFocusable = false
          }
          .component

        if (toolbar != null) {
          toolbar.putClientProperty(ActionToolbarImpl.USE_BASELINE_KEY, true)
          cell(toolbar)
            .resizableColumn()
            .align(AlignX.RIGHT)
            .customize(UnscaledGaps(left = 18))
        }
      }

    }

    panel.background = JBUI.CurrentTheme.ComplexPopup.HEADER_BACKGROUND

    val headerInsets = JBUI.CurrentTheme.ComplexPopup.headerInsets()
    @Suppress("UseDPIAwareBorders")
    panel.border = JBUI.Borders.compound(
      JBUI.Borders.customLineBottom(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
      EmptyBorder(0, headerInsets.left, 0, headerInsets.right))

    for (tab in tabNames) {
      //@NlsSafe val shortcut = shortcutSupplier.apply(tab.id)
      tabbedPane.addTab(tab, null, JPanel(), tab)
    }

    add(panel)

    tabbedPane.bindSelectedTabIn(selectedTabState, coroutineScope)
  }
}