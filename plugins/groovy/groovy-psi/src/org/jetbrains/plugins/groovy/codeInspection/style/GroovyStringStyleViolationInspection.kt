// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SeparatorFactory
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import kotlin.reflect.KMutableProperty

class GroovyStringStyleViolationInspection : BaseInspection() {

  var inspectGradle: Boolean = true
  var inspectScripts: Boolean = true

  private val kindMap = sortedMapOf(
    "Double-quoted string" to "dqs",
    "Single-quoted string" to "sqs",
    "Slashy string" to "ss",
    "Triple-quoted string" to "tqs"
  )


  var currentVersion = ""
  var escapeVersion = ""
  var interpolationVersion = ""


  private fun createStringKindComboBox(@Nls description: String, field: KMutableProperty<String>, id : Int, panel : JPanel, constraints: GridBagConstraints) {
    constraints.gridy = id
    constraints.gridx = 0
    panel.add(JLabel(description), constraints)
    val comboBox: JComboBox<String> = ComboBox(kindMap.keys.toTypedArray())

    comboBox.selectedItem = "Double-quoted string"

    comboBox.addItemListener { e ->
      val item = e.item
      if (item is String) {
        val temp: String = kindMap[item]!!
        if (field.call() != temp) {
          field.setter.call(temp)
        }
      }
    }
    constraints.gridx = 1
    panel.add(comboBox, constraints)
  }

  override fun createOptionsPanel(): JComponent {
    val compressingPanel = JPanel(BorderLayout())
    val containerPanel = JPanel()
    containerPanel.border = JBUI.Borders.empty()
    containerPanel.layout = BoxLayout(containerPanel, BoxLayout.Y_AXIS)
    containerPanel.add(SeparatorFactory.createSeparator("Preferable kind", null))
    val newPanel = JPanel(GridBagLayout())
    containerPanel.add(newPanel, BorderLayout.NORTH)
    val constraints = GridBagConstraints().apply {
      weightx = 1.0; weighty = 1.0; fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.WEST
    }
    createStringKindComboBox("Default", ::currentVersion, 0, newPanel, constraints)
    createStringKindComboBox("Strings with escaping", ::escapeVersion, 1, newPanel, constraints)
    createStringKindComboBox("Strings with interpolation", ::escapeVersion, 2, newPanel, constraints)
    containerPanel.add(SeparatorFactory.createSeparator("Domain of usage", null))
    containerPanel.add(SingleCheckboxOptionsPanel("Inspect Gradle files", this, "inspectGradle"))
    containerPanel.add(SingleCheckboxOptionsPanel("Inspect script files", this, "inspectScripts"))
    compressingPanel.add(containerPanel, BorderLayout.NORTH)
    return compressingPanel
  }

  override fun buildVisitor(): BaseInspectionVisitor = object : BaseInspectionVisitor() {
  }
}
