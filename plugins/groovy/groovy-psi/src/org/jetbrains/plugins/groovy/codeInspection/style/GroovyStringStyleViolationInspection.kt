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
import org.jetbrains.plugins.groovy.codeInspection.style.GroovyStringStyleViolationInspection.ErrorKind.*
import org.jetbrains.plugins.groovy.codeInspection.style.GroovyStringStyleViolationInspection.StringKind.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil
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
    TRIPLE_QUOTED,
    TRIPLE_DOUBLE_QUOTED;

    override fun toString(): String {
      return when (this) {
        UNDEFINED -> "Do not handle specifically"
        DOUBLE_QUOTED -> "Double-quoted string"
        SINGLE_QUOTED -> "Single-quoted string"
        SLASHY -> "Slashy string"
        TRIPLE_QUOTED -> "Triple-quoted string"
        TRIPLE_DOUBLE_QUOTED -> "Triple-double-quoted string"
      }
    }
  }

  @Volatile
  var currentVersion = DOUBLE_QUOTED

  @Volatile
  var escapeVersion = UNDEFINED

  @Volatile
  var interpolationVersion = UNDEFINED

  @Volatile
  var multilineVersion = TRIPLE_QUOTED

  @Volatile
  var inspectGradle: Boolean = true

  @Volatile
  var inspectScripts: Boolean = true


  private fun JPanel.addStringKindComboBox(@Nls description: String,
                                           field: KMutableProperty<StringKind>,
                                           values: Array<StringKind>,
                                           id: Int,
                                           constraints: GridBagConstraints) {
    constraints.gridy = id
    constraints.gridx = 0
    add(JLabel(description), constraints)
    val comboBox: JComboBox<StringKind> = ComboBox(values)
    comboBox.renderer = SimpleListCellRenderer.create("") { it.toString() }

    comboBox.selectedItem = field.getter.call()

    comboBox.addItemListener { e ->
      val selectedItem = e.item
      if (field.getter.call() != selectedItem) {
        field.setter.call(selectedItem)
      }
    }
    constraints.gridx = 1
    add(comboBox, constraints)
  }

  private fun generateComboBoxes(): JPanel = JPanel(GridBagLayout()).apply {
    val constraints = GridBagConstraints().apply {
      weightx = 1.0; weighty = 1.0; fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.WEST
    }
    val activeStringKinds = arrayOf(DOUBLE_QUOTED, SINGLE_QUOTED, SLASHY, TRIPLE_QUOTED)
    addStringKindComboBox("Default", ::currentVersion, activeStringKinds, 0, constraints)
    addStringKindComboBox("Strings with escaping", ::escapeVersion, StringKind.values(), 1, constraints)
    addStringKindComboBox("Strings with interpolation", ::interpolationVersion, StringKind.values(), 2, constraints)
    addStringKindComboBox("Multiline string", ::multilineVersion, arrayOf(UNDEFINED, TRIPLE_QUOTED, TRIPLE_DOUBLE_QUOTED), 3, constraints)
  }

  private enum class ErrorKind {
    PLAIN_STRING_SHOULD_BE_DOUBLE_QUOTED,
    PLAIN_STRING_SHOULD_BE_SINGLE_QUOTED,
    PLAIN_STRING_SHOULD_BE_SLASHY_QUOTED,
    PLAIN_STRING_SHOULD_BE_TRIPLE_QUOTED,
    PLAIN_STRING_SHOULD_BE_DOUBLY_TRIPLE_QUOTED,
    MULTILINE_STRING_SHOULD_BE_TRIPLE_QUOTED,
    MULTILINE_STRING_SHOULD_BE_DOUBLY_TRIPLE_QUOTED
  }


  override fun buildErrorString(vararg args: Any?): String {
    return when (args[0]!! as ErrorKind) {
      PLAIN_STRING_SHOULD_BE_DOUBLE_QUOTED -> "Plain string should be double-quoted"
      PLAIN_STRING_SHOULD_BE_SINGLE_QUOTED -> "Plain string should be single-quoted"
      PLAIN_STRING_SHOULD_BE_SLASHY_QUOTED -> "Plain string should be slashy-quoted"
      PLAIN_STRING_SHOULD_BE_TRIPLE_QUOTED -> "Plain string should be quoted with '''"
      PLAIN_STRING_SHOULD_BE_DOUBLY_TRIPLE_QUOTED -> "Plain string should quoted with \"\"\""
      MULTILINE_STRING_SHOULD_BE_TRIPLE_QUOTED -> "Multiline string should be quoted with '''"
      MULTILINE_STRING_SHOULD_BE_DOUBLY_TRIPLE_QUOTED -> "Multiline string should be quoted with \"\"\""
    }
  }


  override fun createOptionsPanel(): JComponent {
    val compressingPanel = JPanel(BorderLayout())
    val containerPanel = JPanel()
    containerPanel.border = JBUI.Borders.empty()
    containerPanel.layout = BoxLayout(containerPanel, BoxLayout.Y_AXIS)
    containerPanel.add(SeparatorFactory.createSeparator("Preferable kind", null))
    containerPanel.add(generateComboBoxes(), BorderLayout.NORTH)

    containerPanel.apply {
      add(SeparatorFactory.createSeparator("Domain of usage", null))
      add(SingleCheckboxOptionsPanel("Inspect Gradle files", this@GroovyStringStyleViolationInspection, "inspectGradle"))
      add(SingleCheckboxOptionsPanel("Inspect script files", this@GroovyStringStyleViolationInspection, "inspectScripts"))
    }

    compressingPanel.add(containerPanel, BorderLayout.NORTH)
    return compressingPanel
  }

  override fun buildVisitor(): BaseInspectionVisitor = object : BaseInspectionVisitor() {
    override fun visitLiteralExpression(literal: GrLiteral) {
      if (literal !is GrString) {
        if (multilineVersion != UNDEFINED && GrStringUtil.isMultilineStringLiteral(literal)) {
          checkInconsistency(multilineVersion, literal,
                             mapOf(TRIPLE_QUOTED to MULTILINE_STRING_SHOULD_BE_TRIPLE_QUOTED,
                                   TRIPLE_DOUBLE_QUOTED to MULTILINE_STRING_SHOULD_BE_DOUBLY_TRIPLE_QUOTED))
        }
        else {
          checkInconsistency(currentVersion, literal,
                             mapOf(DOUBLE_QUOTED to PLAIN_STRING_SHOULD_BE_DOUBLE_QUOTED,
                                   SINGLE_QUOTED to PLAIN_STRING_SHOULD_BE_SINGLE_QUOTED,
                                   SLASHY to PLAIN_STRING_SHOULD_BE_SLASHY_QUOTED,
                                   TRIPLE_QUOTED to PLAIN_STRING_SHOULD_BE_TRIPLE_QUOTED,
                                   TRIPLE_DOUBLE_QUOTED to PLAIN_STRING_SHOULD_BE_DOUBLY_TRIPLE_QUOTED))
        }
      }
      super.visitLiteralExpression(literal)
    }


    private fun checkInconsistency(expected: StringKind, literal: GrLiteral, errorKinds: Map<StringKind, ErrorKind>) {
      fun doCheck(kind: StringKind, predicate: (GrLiteral) -> Boolean) {
        val errorKind = errorKinds[expected]
        if (errorKind != null && expected == kind && !predicate(literal)) {
          registerError(literal, errorKind)
        }
      }
      doCheck(SINGLE_QUOTED, GrStringUtil::isSingleQuoteString)
      doCheck(DOUBLE_QUOTED, GrStringUtil::isDoubleQuoteString)
      doCheck(TRIPLE_QUOTED, GrStringUtil::isTripleQuoteString)
      doCheck(TRIPLE_DOUBLE_QUOTED, GrStringUtil::isTripleDoubleQuoteString)
      doCheck(SLASHY, GrStringUtil::isSlashyString)
    }

  }


}
