// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SeparatorFactory
import com.intellij.ui.SimpleListCellRenderer
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

  enum class StringKind {
    UNDEFINED,
    DOUBLE_QUOTED,
    SINGLE_QUOTED,
    SLASHY,
    TRIPLE_QUOTED;

    override fun toString(): String {
      return when (this) {
        UNDEFINED -> "Do not handle specifically"
        DOUBLE_QUOTED -> "Double-quoted string"
        SINGLE_QUOTED -> "Single-quoted string"
        SLASHY -> "Slashy string"
        TRIPLE_QUOTED -> "Triple-quoted string"
      }
    }
  }

  @Volatile
  var currentVersion = StringKind.DOUBLE_QUOTED

  @Volatile
  var escapeVersion = StringKind.UNDEFINED

  @Volatile
  var interpolationVersion = StringKind.UNDEFINED

  @Volatile
  var inspectGradle: Boolean = true

  @Volatile
  var inspectScripts: Boolean = true


  private fun JPanel.addStringKindComboBox(@Nls description: String,
                                           field: KMutableProperty<StringKind>,
                                           values: Array<StringKind>,
                                           defaultValue: StringKind,
                                           id: Int,
                                           constraints: GridBagConstraints) {
    constraints.gridy = id
    constraints.gridx = 0
    add(JLabel(description), constraints)
    val comboBox: JComboBox<StringKind> = ComboBox(values)
    comboBox.renderer = SimpleListCellRenderer.create("") { it.toString() }

    comboBox.selectedItem = defaultValue

    comboBox.addItemListener { e ->
      val selectedItem = e.item
      if (field.getter.call() != selectedItem) {
        field.setter.call(selectedItem)
      }
    }
    constraints.gridx = 1
    add(comboBox, constraints)
  }

  private fun generateComboBoxes(panel: JPanel) = panel.apply {
    val constraints = GridBagConstraints().apply {
      weightx = 1.0; weighty = 1.0; fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.WEST
    }
    val activeStringKinds = arrayOf(StringKind.DOUBLE_QUOTED, StringKind.SINGLE_QUOTED, StringKind.SLASHY, StringKind.TRIPLE_QUOTED)
    addStringKindComboBox("Default", ::currentVersion, activeStringKinds, StringKind.DOUBLE_QUOTED, 0, constraints)
    addStringKindComboBox("Strings with escaping", ::escapeVersion, StringKind.values(), StringKind.UNDEFINED, 1, constraints)
    addStringKindComboBox("Strings with interpolation", ::interpolationVersion, StringKind.values(), StringKind.UNDEFINED, 2, constraints)
  }

  override fun createOptionsPanel(): JComponent {
    val compressingPanel = JPanel(BorderLayout())
    val containerPanel = JPanel()
    containerPanel.border = JBUI.Borders.empty()
    containerPanel.layout = BoxLayout(containerPanel, BoxLayout.Y_AXIS)
    containerPanel.add(SeparatorFactory.createSeparator("Preferable kind", null))
    val newPanel = JPanel(GridBagLayout())
    containerPanel.add(newPanel, BorderLayout.NORTH)

    generateComboBoxes(newPanel)

    containerPanel.apply {
      add(SeparatorFactory.createSeparator("Domain of usage", null))
      add(SingleCheckboxOptionsPanel("Inspect Gradle files", this@GroovyStringStyleViolationInspection, "inspectGradle"))
      add(SingleCheckboxOptionsPanel("Inspect script files", this@GroovyStringStyleViolationInspection, "inspectScripts"))
    }
    compressingPanel.add(containerPanel, BorderLayout.NORTH)
    return compressingPanel
  }

  override fun buildVisitor(): BaseInspectionVisitor = object : BaseInspectionVisitor() {
  }
}
