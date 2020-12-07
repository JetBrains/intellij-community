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
import org.jetbrains.plugins.groovy.codeInspection.style.GroovyStringStyleViolationInspection.StringKind.*
import org.jetbrains.plugins.groovy.codeInspection.style.GroovyStringStyleViolationInspection.TargetKind.*
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
    TRIPLE_DOUBLE_QUOTED,
    DOLLAR_SLASHY_QUOTED;

    override fun toString(): String {
      return when (this) {
        UNDEFINED -> "Do not handle specifically"
        DOUBLE_QUOTED -> "Double-quoted string"
        SINGLE_QUOTED -> "Single-quoted string"
        SLASHY -> "Slashy string"
        TRIPLE_QUOTED -> "Triple-quoted string"
        TRIPLE_DOUBLE_QUOTED -> "Triple-double-quoted string"
        DOLLAR_SLASHY_QUOTED -> "Dollar-slashy string"
      }
    }
  }

  companion object {
    val PLAIN_STRING_OPTIONS = arrayOf(DOUBLE_QUOTED, SINGLE_QUOTED, SLASHY, TRIPLE_QUOTED, TRIPLE_DOUBLE_QUOTED, DOLLAR_SLASHY_QUOTED)
    val MULTILINE_STRING_OPTIONS = arrayOf(UNDEFINED, TRIPLE_QUOTED, SLASHY, TRIPLE_DOUBLE_QUOTED, DOLLAR_SLASHY_QUOTED)
    val ESCAPED_STRING_OPTIONS = arrayOf(DOUBLE_QUOTED, SINGLE_QUOTED, SLASHY, TRIPLE_QUOTED, TRIPLE_DOUBLE_QUOTED, DOLLAR_SLASHY_QUOTED)
    val INTERPOLATED_STRING_OPTIONS = arrayOf(UNDEFINED, DOUBLE_QUOTED, SLASHY, TRIPLE_DOUBLE_QUOTED, DOLLAR_SLASHY_QUOTED)
  }

  @Volatile
  var plainVersion = SINGLE_QUOTED

  @Volatile
  var escapeVersion = UNDEFINED

  @Volatile
  var interpolationVersion = UNDEFINED

  @Volatile
  var multilineVersion = TRIPLE_QUOTED

  @Volatile
  var inspectGradle: Boolean = true

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
    addStringKindComboBox("Default", ::plainVersion, PLAIN_STRING_OPTIONS, 0, constraints)
    addStringKindComboBox("Strings with escaping", ::escapeVersion, arrayOf(UNDEFINED, *ESCAPED_STRING_OPTIONS), 1, constraints)
    addStringKindComboBox("Strings with interpolation", ::interpolationVersion, arrayOf(UNDEFINED, *INTERPOLATED_STRING_OPTIONS), 2, constraints)
    addStringKindComboBox("Multiline string", ::multilineVersion, arrayOf(UNDEFINED, *MULTILINE_STRING_OPTIONS), 3, constraints)
  }

  private enum class TargetKind {
    PLAIN_STRING, MULTILINE_STRING, ESCAPED_STRING, INTERPOLATED_STRING
  }


  override fun buildErrorString(vararg args: Any?): String {
    val carrierString = args[0] as TargetKind
    val desiredKind = args[1] as StringKind
    return when (carrierString) {
      PLAIN_STRING -> when (desiredKind) {
        DOUBLE_QUOTED -> "Plain string should be double-quoted"
        SINGLE_QUOTED -> "Plain string should be single-quoted"
        SLASHY -> "Plain string should be slashy-quoted"
        DOLLAR_SLASHY_QUOTED -> "Plain string should be dollar-slashy-quoted"
        TRIPLE_QUOTED -> "Plain string should be quoted with '''"
        TRIPLE_DOUBLE_QUOTED -> "Plain string should be quoted with \"\"\""
        else -> error("Unexpected error message")
      }
      MULTILINE_STRING -> when (desiredKind) {
        TRIPLE_QUOTED -> "Multiline string should be quoted with '''"
        TRIPLE_DOUBLE_QUOTED -> "Multiline string should be quoted with \"\"\""
        SLASHY -> "Multiline string should be slashy-quoted"
        DOLLAR_SLASHY_QUOTED -> "Multiline string should be dollar-slashy-quoted"
        else -> error("Unexpected error message")
      }
      ESCAPED_STRING -> "Escaping could be minimized"
      INTERPOLATED_STRING -> when (desiredKind) {
        DOUBLE_QUOTED -> "Interpolated string should be double-quoted"
        DOLLAR_SLASHY_QUOTED -> "Interpolated string should be dollar-slashy-quoted"
        SLASHY -> "Interpolated string should be slashy-quoted"
        TRIPLE_DOUBLE_QUOTED -> "Interpolated string should be quoted with \"\"\""
        else -> error("Unexpected error message")
      }
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
    }

    compressingPanel.add(containerPanel, BorderLayout.NORTH)
    return compressingPanel
  }

  override fun buildVisitor(): BaseInspectionVisitor = object : BaseInspectionVisitor() {
    override fun visitLiteralExpression(literal: GrLiteral) {
      if (literal is GrString) {
        handleGString(literal)
      }
      else {
        handlePlainString(literal)
      }
      super.visitLiteralExpression(literal)
    }


    private fun handleGString(literal: GrLiteral) {
      checkInconsistency(interpolationVersion, literal, INTERPOLATED_STRING, INTERPOLATED_STRING_OPTIONS)
    }

    private fun handlePlainString(literal: GrLiteral) {
      if (multilineVersion != UNDEFINED && GrStringUtil.isMultilineStringLiteral(literal) && literal.text.contains("\n")) {
        checkInconsistency(multilineVersion, literal, MULTILINE_STRING, MULTILINE_STRING_OPTIONS)
        return
      }
      else if (escapeVersion != UNDEFINED) {
        val bestEscaping = findBestEscaping(literal, escapeVersion, plainVersion)
        if (bestEscaping != null) {
          if (bestEscaping.second != 0) {
            checkInconsistency(bestEscaping.first, literal, ESCAPED_STRING, ESCAPED_STRING_OPTIONS)
          }
          return
        }
      }
      checkInconsistency(plainVersion, literal, PLAIN_STRING, PLAIN_STRING_OPTIONS)
    }

    private fun checkInconsistency(expected: StringKind,
                                   literal: GrLiteral,
                                   carrierKind: TargetKind,
                                   availableStringKinds: Array<StringKind>) {
      if (expected !in availableStringKinds) {
        return
      }
      fun doCheck(predicate: (GrLiteral) -> Boolean) {
        if (!predicate(literal)) {
          registerError(literal, carrierKind, expected)
        }
      }
      when (expected) {
        DOUBLE_QUOTED -> doCheck(GrStringUtil::isDoubleQuoteString)
        SINGLE_QUOTED -> doCheck(GrStringUtil::isSingleQuoteString)
        SLASHY -> doCheck(GrStringUtil::isSlashyString)
        TRIPLE_QUOTED -> doCheck(GrStringUtil::isTripleQuoteString)
        TRIPLE_DOUBLE_QUOTED -> doCheck(GrStringUtil::isTripleDoubleQuoteString)
        DOLLAR_SLASHY_QUOTED -> doCheck(GrStringUtil::isDollarSlashyString)
        else -> Unit
      }
    }

  }

  private fun findBestEscaping(literal: GrLiteral, preferredKindInEscaping: StringKind, mainKind: StringKind): Pair<StringKind, Int>? {
    val stringContent = literal.text
    val startQuote = GrStringUtil.getStartQuote(stringContent)
    val meaningfulText = GrStringUtil.unescapeString(GrStringUtil.removeQuotes(stringContent))
    val doubleQuotes = meaningfulText.count { it == '"' }
    val singleQuotes = meaningfulText.count { it == '\'' }
    val dollars = meaningfulText.count { it == '$' }
    val windows = meaningfulText.windowed(3)
    val tripleQuotes = windows.count { it == GrStringUtil.TRIPLE_QUOTES }
    val tripleDoubleQuotes = windows.count { it == GrStringUtil.TRIPLE_DOUBLE_QUOTES }
    val slashes = meaningfulText.count { it == '/' }
    val reversedSlashes = meaningfulText.count { it == '\\' }
    val currentEscapingScore = when (startQuote) {
      GrStringUtil.QUOTE -> singleQuotes + reversedSlashes
      GrStringUtil.DOUBLE_QUOTES -> doubleQuotes + reversedSlashes
      GrStringUtil.TRIPLE_QUOTES -> tripleQuotes
      GrStringUtil.TRIPLE_DOUBLE_QUOTES -> tripleDoubleQuotes
      GrStringUtil.DOLLAR_SLASH -> dollars
      GrStringUtil.SLASH -> slashes
      else -> return null
    }
    val bestEscaping = listOf(DOUBLE_QUOTED to doubleQuotes + reversedSlashes,
                              SINGLE_QUOTED to singleQuotes + reversedSlashes,
                              TRIPLE_QUOTED to tripleQuotes,
                              SLASHY to slashes,
                              DOLLAR_SLASHY_QUOTED to dollars,
                              TRIPLE_DOUBLE_QUOTED to tripleDoubleQuotes)
      .map { it.first to currentEscapingScore - it.second }
      .maxWithOrNull { (kind, num), (kind2, num2) ->
        if (num == num2) {
          if (kind == mainKind) return@maxWithOrNull 1
          if (kind2 == mainKind) return@maxWithOrNull -1
          if (kind == preferredKindInEscaping) return@maxWithOrNull 1
          if (kind2 == preferredKindInEscaping) return@maxWithOrNull -1
          kind.toString().compareTo(kind2.toString()) // induce total order
        }
        else {
          num.compareTo(num2)
        }
      }
    return bestEscaping?.takeIf { it.first != mainKind && it.second >= 0 }
  }


}
