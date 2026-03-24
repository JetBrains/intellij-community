// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.pluginChooser

import com.intellij.ide.startup.importSettings.data.WizardPlugin
import com.intellij.ui.components.Badge
import com.intellij.ui.components.Badge.ColorType
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.builder.components.DslLabelType
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.Box
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel

class WizardPluginPane(val plugin: WizardPlugin, changeHandler: () -> Unit) {
  private var checkBox = JBCheckBox().apply {
    addItemListener { e ->
      changeHandler()
    }
    isOpaque = false
  }

  val selected: Boolean
    get() = checkBox.isSelected

  val pane: JPanel = JPanel(GridBagLayout()).apply {
    val c = GridBagConstraints()

    plugin.description?.let {
      c.gridx = 0
      c.gridy = 0
      c.gridheight = 2
      c.weightx = 0.0
      c.anchor = GridBagConstraints.CENTER
      add(checkBox, c)

      c.gridx = 1
      c.gridy = 0
      c.gridheight = 2
      c.anchor = GridBagConstraints.CENTER
      add(JLabel(plugin.icon).apply {
        border = JBUI.Borders.empty(0, 10)
      }, c)

      c.gridx = 2
      c.gridy = 0
      c.gridheight = 1
      c.anchor = GridBagConstraints.SOUTHWEST
      c.weightx = 1.0
      c.fill = GridBagConstraints.HORIZONTAL
      add(createNamePanel(plugin.name, plugin.badge), c)

      c.gridx = 2
      c.gridy = 1
      c.anchor = GridBagConstraints.NORTHWEST
      c.insets = JBUI.insetsTop(6)
      add(createLabel().apply {
        text = plugin.description
        foreground = UIUtil.getLabelDisabledForeground()
      }, c)

    } ?: run {
      c.gridx = 0
      c.gridy = 0
      c.weightx = 0.0
      add(checkBox, c)
      c.gridx = 1
      c.gridy = 0
      add(JLabel(plugin.icon).apply {
        border = JBUI.Borders.empty(0, 10)
      }, c)
      c.gridx = 2
      c.gridy = 0
      c.weightx = 1.0
      c.fill = GridBagConstraints.HORIZONTAL
      add(createNamePanel(plugin.name, plugin.badge), c)
    }
  }.apply {
    isOpaque = false
    border = JBUI.Borders.empty(8, 20)
  }

  private fun createNamePanel(name: @Nls String, badge: @Nls String?): JPanel {
    val panel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply { isOpaque = false }
    panel.add(createLabel().apply { text = name })
    if (badge != null) {
      panel.add(Box.createHorizontalStrut(JBUI.scale(6)))
      panel.add(JBLabel(Badge(badge, ColorType.PURPLE_SECONDARY)))
    }
    return panel
  }

  private fun createLabel(): JEditorPane {
    val dslLabel = DslLabel(DslLabelType.LABEL)
    dslLabel.action = HyperlinkEventAction.HTML_HYPERLINK_INSTANCE
    dslLabel.maxLineLength = MAX_LINE_LENGTH_WORD_WRAP

    return dslLabel
  }
}