// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.pluginChooser

import com.intellij.ide.startup.importSettings.data.WizardPlugin
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.components.DslLabel
import com.intellij.ui.dsl.builder.components.DslLabelType
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
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

  val pane = JPanel(GridBagLayout()).apply {
    val c = GridBagConstraints()

    plugin.description?.let {
      c.gridx = 0
      c.gridy = 1
      c.weightx = 0.0
      add(checkBox, c)

      c.gridx = 1
      c.gridy = 0
      c.gridheight = 3
      c.anchor = GridBagConstraints.NORTH
      add(JLabel(plugin.icon).apply {
        border = JBUI.Borders.empty(0, 10)
      }, c)

      c.gridx = 2
      c.gridy = 0
      c.gridheight = 1
      c.anchor = GridBagConstraints.CENTER
      add(JPanel().apply {
        isOpaque = false }
      )

      c.gridx = 2
      c.gridy = 1
      c.weightx = 1.0
      c.fill = GridBagConstraints.HORIZONTAL
      add(createLabel().apply {
        text = plugin.name
      }, c)

      c.gridx = 2
      c.gridy = 2
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
      add(createLabel().apply {
        text = plugin.name
      }, c)
    }
  }.apply {
    isOpaque = false
    border = JBUI.Borders.empty(0, 20)
  }

  private fun createLabel(): JEditorPane {
    val dslLabel = DslLabel(DslLabelType.LABEL)
    dslLabel.action = HyperlinkEventAction.HTML_HYPERLINK_INSTANCE
    dslLabel.maxLineLength = MAX_LINE_LENGTH_WORD_WRAP

    return dslLabel
  }
}