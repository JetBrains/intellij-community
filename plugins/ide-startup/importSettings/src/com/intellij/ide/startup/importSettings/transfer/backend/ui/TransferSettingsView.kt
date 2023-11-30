// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.ui

import com.intellij.ide.startup.importSettings.TransferSettingsConfiguration
import com.intellij.ide.startup.importSettings.controllers.TransferSettingsListener
import com.intellij.ide.startup.importSettings.fus.TransferSettingsCollector
import com.intellij.ide.startup.importSettings.models.*
import com.intellij.ide.startup.importSettings.ui.representation.TransferSettingsRepresentationPanel
import com.intellij.ide.startup.importSettings.ui.representation.TransferSettingsRightPanelChooser
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JLabel
import javax.swing.JPanel

class TransferSettingsView(private val config: TransferSettingsConfiguration, private val model: TransferSettingsModel) {
  val selectedIde: BaseIdeVersion? get() = leftPanel.list.selectedValue

  private val leftPanel = TransferSettingsLeftPanel(model.listModel)
  private val contentPanel = JPanel(MigLayout("ins 0, novisualpadding, fill"))

  private val cachedViews = mutableMapOf<BaseIdeVersion, TransferSettingsRepresentationPanel>()

  val panel = initPanel()

  init {
    config.controller.addListener(object : TransferSettingsListener {
      override fun reloadPerformed(ideVersion: FailedIdeVersion) {
        cachedViews.remove(ideVersion)
        performRefresh(ideVersion.id)
      }

      override fun importStarted(ideVersion: IdeVersion, settings: Settings) {
        cachedViews[selectedIde]?.block()
        leftPanel.list.isEnabled = false
      }
    })
  }

  private fun initPanel() = JPanel().apply {
    layout = MigLayout("ins 0, novisualpadding, fill")
    addComponentListener(object : ComponentAdapter() {
      private var shown = false

      override fun componentShown(e: ComponentEvent?) = onEvent()
      override fun componentResized(e: ComponentEvent?) = onEvent()

      private fun onEvent() {
        if (shown) return
        TransferSettingsCollector.logTransferSettingsShown()
        shown = true
      }
    })

    if (model.shouldShowLeftPanel) {
      add(leftPanel, "west, width 250px, wmax 250px, wmin 250px, growy, pushy, spany")
    }
    add(contentPanel, "east, grow, push, span")
    contentPanel.add(JLabel())

    border = JBUI.Borders.customLineBottom(JBColor.border())

    var previousSelected: BaseIdeVersion? = null

    leftPanel.addListSelectionListener {
      if (selectedValue == null) return@addListSelectionListener
      if (previousSelected == selectedValue) return@addListSelectionListener
      previousSelected = selectedValue
      val view = cachedViews.getOrPut(selectedValue) {
        val chooser = (selectedValue as? IdeVersion)?.let { it.provider.getRightPanel(it, config) }
                      ?: TransferSettingsRightPanelChooser(selectedValue, config)
        chooser.select().apply {
          onStateChange {
            (selectedValue as? IdeVersion)?.let { it1 -> config.controller.updateCheckboxes(it1) }
          }
        }
      }
      contentPanel.apply {
        removeAll()
        add(view.getComponent(), "grow, push, span, wmax 100%")
        revalidate()
        repaint()
      }
      config.controller.itemSelected(selectedValue)
    }

    performRefresh(null)
  }

  private fun performRefresh(selectionTargetId: String?) {
    val newOrdered = model.performRefresh()

    leftPanel.list.selectedIndex = if (selectionTargetId != null) newOrdered.indexOfFirst { it.id == selectionTargetId } else 0
  }
}
