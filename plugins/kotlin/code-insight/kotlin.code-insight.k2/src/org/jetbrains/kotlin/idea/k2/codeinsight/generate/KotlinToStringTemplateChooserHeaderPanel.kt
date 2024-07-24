// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.generate

import com.intellij.java.JavaBundle
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBLabel
import org.jetbrains.java.generate.template.TemplateResource
import org.jetbrains.java.generate.view.TemplatesPanel
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel

internal class KotlinToStringTemplateChooserHeaderPanel(val project: Project) : JPanel(GridBagLayout()) {
  private val comboBox: JComboBox<TemplateResource>

  init {
    val templatesManager = KotlinToStringTemplatesManager.getInstance()
    val templates = templatesManager.getAllTemplates()

    val settingsButton = JButton(JavaBundle.message("button.text.settings"))
    settingsButton.setMnemonic(KeyEvent.VK_S)

    comboBox = ComboBox<TemplateResource>(templates.toTypedArray<TemplateResource>())

    comboBox.setRenderer(SimpleListCellRenderer.create<TemplateResource>(
      SimpleListCellRenderer.Customizer { label: JBLabel, value: TemplateResource, index: Int ->
        label.setText(value.name)
      }))
    settingsButton.addActionListener(object : ActionListener {
      override fun actionPerformed(e: ActionEvent?) {
        val ui = TemplatesPanel(project, KotlinToStringTemplatesManager.getInstance())
        if (ShowSettingsUtil.getInstance()
          .editConfigurable(this@KotlinToStringTemplateChooserHeaderPanel, ui, Runnable { ui.selectItem(templatesManager.getDefaultTemplate()) })) {
            comboBox.removeAllItems()
            for (resource in templatesManager.allTemplates) {
                comboBox.addItem(resource)
            }
            comboBox.setSelectedItem(templatesManager.getDefaultTemplate())
        }
      }
    })

    comboBox.setSelectedItem(templatesManager.getDefaultTemplate())

    val templatesLabel = JLabel(JavaBundle.message("generate.tostring.template.label"))
    templatesLabel.setLabelFor(comboBox)

    val constraints = GridBagConstraints()
    constraints.anchor = GridBagConstraints.BASELINE
    constraints.gridx = 0
    add(templatesLabel, constraints)
    constraints.gridx = 1
    constraints.weightx = 1.0
    constraints.fill = GridBagConstraints.HORIZONTAL
    add(comboBox, constraints)
    constraints.gridx = 2
    constraints.weightx = 0.0
    add(settingsButton, constraints)
  }

  fun getSelectedTemplate(): TemplateResource? {
    return comboBox.selectedItem as? TemplateResource
  }
}