// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SeparatorFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.codeInspection.style.GrStringStyleViolationInspection.InspectionStringKind.*
import org.jetbrains.plugins.groovy.codeInspection.style.GrStringStyleViolationInspection.TargetKind.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*
import kotlin.reflect.KMutableProperty
import org.jetbrains.plugins.groovy.lang.psi.util.StringKind as OuterStringKind


class GrStringStyleViolationInspection : BaseInspection() {

  internal enum class InspectionStringKind {
    UNDEFINED,
    DOUBLE_QUOTED,
    SINGLE_QUOTED,
    SLASHY,
    TRIPLE_QUOTED,
    TRIPLE_DOUBLE_QUOTED,
    DOLLAR_SLASHY_QUOTED;

    @Nls
    override fun toString(): String {
      return when (this) {
        UNDEFINED -> GroovyBundle.message("string.option.do.not.handle.specifically")
        DOUBLE_QUOTED -> GroovyBundle.message("string.option.double.quoted.string")
        SINGLE_QUOTED -> GroovyBundle.message("string.option.single.quoted.string")
        SLASHY -> GroovyBundle.message("string.option.slashy.string")
        TRIPLE_QUOTED -> GroovyBundle.message("string.option.triple.quoted.string")
        TRIPLE_DOUBLE_QUOTED -> GroovyBundle.message("string.option.triple.double.quoted.string")
        DOLLAR_SLASHY_QUOTED -> GroovyBundle.message("string.option.dollar.slashy.string")
      }
    }
  }

  companion object {
    private val PLAIN_STRING_OPTIONS = arrayOf(DOUBLE_QUOTED, SINGLE_QUOTED, SLASHY, TRIPLE_QUOTED, TRIPLE_DOUBLE_QUOTED, DOLLAR_SLASHY_QUOTED)
    private val MULTILINE_STRING_OPTIONS = arrayOf(TRIPLE_QUOTED, SLASHY, TRIPLE_DOUBLE_QUOTED, DOLLAR_SLASHY_QUOTED)
    private val ESCAPED_STRING_OPTIONS = arrayOf(DOUBLE_QUOTED, SINGLE_QUOTED, SLASHY, TRIPLE_QUOTED, TRIPLE_DOUBLE_QUOTED, DOLLAR_SLASHY_QUOTED)
    private val INTERPOLATED_STRING_OPTIONS = arrayOf(DOUBLE_QUOTED, SLASHY, TRIPLE_DOUBLE_QUOTED, DOLLAR_SLASHY_QUOTED)
  }

  @Volatile
  internal var plainVersion = SINGLE_QUOTED

  @Volatile
  internal var escapeVersion = UNDEFINED

  @Volatile
  internal var interpolationVersion = UNDEFINED

  @Volatile
  internal var multilineVersion = TRIPLE_QUOTED

  @Volatile
  internal var inspectGradle: Boolean = true

  private fun JPanel.addStringKindComboBox(@Nls description: String,
                                           field: KMutableProperty<InspectionStringKind>,
                                           values: Array<InspectionStringKind>,
                                           id: Int,
                                           constraints: GridBagConstraints) {
    constraints.gridy = id
    constraints.gridx = 0
    add(JLabel(description), constraints)
    val comboBox: JComboBox<InspectionStringKind> = ComboBox(values)
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
    addStringKindComboBox(GroovyBundle.message("string.sort.default"), ::plainVersion, PLAIN_STRING_OPTIONS, 0, constraints)
    addStringKindComboBox(GroovyBundle.message("string.sort.strings.with.escaping"), ::escapeVersion,
                          arrayOf(UNDEFINED, *ESCAPED_STRING_OPTIONS), 1, constraints)
    addStringKindComboBox(GroovyBundle.message("string.sort.strings.with.interpolation"), ::interpolationVersion,
                          arrayOf(UNDEFINED, *INTERPOLATED_STRING_OPTIONS), 2, constraints)
    addStringKindComboBox(GroovyBundle.message("string.sort.multiline.string"), ::multilineVersion,
                          arrayOf(UNDEFINED, *MULTILINE_STRING_OPTIONS), 3, constraints)
  }

  private enum class TargetKind {
    PLAIN_STRING, MULTILINE_STRING, ESCAPED_STRING, INTERPOLATED_STRING
  }


  override fun buildErrorString(vararg args: Any?): String {
    val carrierString = args[0] as TargetKind
    val desiredKind = args[1] as InspectionStringKind
    return when (carrierString) {
      PLAIN_STRING -> when (desiredKind) {
        DOUBLE_QUOTED -> GroovyBundle.message("inspection.message.plain.string.should.be.double.quoted")
        SINGLE_QUOTED -> GroovyBundle.message("inspection.message.plain.string.should.be.single.quoted")
        SLASHY -> GroovyBundle.message("inspection.message.plain.string.should.be.slashy.quoted")
        DOLLAR_SLASHY_QUOTED -> GroovyBundle.message("inspection.message.plain.string.should.be.dollar.slashy.quoted")
        TRIPLE_QUOTED -> GroovyBundle.message("inspection.message.plain.string.should.be.quoted.with.triple.quotes")
        TRIPLE_DOUBLE_QUOTED -> GroovyBundle.message("inspection.message.plain.string.should.be.quoted.with.triple.double.quotes")
        else -> error("Unexpected error message")
      }
      MULTILINE_STRING -> when (desiredKind) {
        TRIPLE_QUOTED -> GroovyBundle.message("inspection.message.multiline.string.should.be.quoted.with.triple.quotes")
        TRIPLE_DOUBLE_QUOTED -> GroovyBundle.message("inspection.message.multiline.string.should.be.quoted.with.triple.double.quotes")
        SLASHY -> GroovyBundle.message("inspection.message.multiline.string.should.be.slashy.quoted")
        DOLLAR_SLASHY_QUOTED -> GroovyBundle.message("inspection.message.multiline.string.should.be.dollar.slashy.quoted")
        else -> error("Unexpected error message")
      }
      ESCAPED_STRING -> GroovyBundle.message("inspection.message.string.escaping.could.be.minimized")
      INTERPOLATED_STRING -> when (desiredKind) {
        DOUBLE_QUOTED -> GroovyBundle.message("inspection.message.interpolated.string.should.be.double.quoted")
        DOLLAR_SLASHY_QUOTED -> GroovyBundle.message("inspection.message.interpolated.string.should.be.dollar.slashy.quoted")
        SLASHY -> GroovyBundle.message("inspection.message.interpolated.string.should.be.slashy.quoted")
        TRIPLE_DOUBLE_QUOTED -> GroovyBundle.message("inspection.message.interpolated.string.should.be.quoted.with.triple.double.quotes")
        else -> error("Unexpected error message")
      }
    }
  }


  override fun createOptionsPanel(): JComponent {
    val compressingPanel = JPanel(BorderLayout())
    val containerPanel = JPanel()
    containerPanel.border = JBUI.Borders.empty()
    containerPanel.layout = BoxLayout(containerPanel, BoxLayout.Y_AXIS)
    containerPanel.add(SeparatorFactory.createSeparator(GroovyBundle.message("separator.preferable.string.kind"), null))
    containerPanel.add(generateComboBoxes(), BorderLayout.NORTH)

    containerPanel.apply {
      add(SeparatorFactory.createSeparator(GroovyBundle.message("separator.domain.of.inspection.usage"), null))
      add(SingleCheckboxOptionsPanel(GroovyBundle.message("checkbox.inspect.gradle.files"), this@GrStringStyleViolationInspection,
                                     "inspectGradle"))
    }

    compressingPanel.add(containerPanel, BorderLayout.NORTH)
    return compressingPanel
  }

  override fun buildVisitor(): BaseInspectionVisitor = object : BaseInspectionVisitor() {
    override fun visitLiteralExpression(literal: GrLiteral) {
      if (!inspectGradle && literal.containingFile.fileType.defaultExtension == "gradle") {
        return
      }
      if (literal is GrString) {
        handleGString(literal)
      }
      else if (GrStringUtil.getStartQuote(literal.text) != "") {
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
      if ("\n" !in literal.text) {
        checkInconsistency(plainVersion, literal, PLAIN_STRING, PLAIN_STRING_OPTIONS)
      }
    }

    private fun checkInconsistency(expected: InspectionStringKind,
                                   literal: GrLiteral,
                                   carrierKind: TargetKind,
                                   availableStringKinds: Array<InspectionStringKind>) {
      if (expected !in availableStringKinds) {
        return
      }
      fun doCheck(predicate: (GrLiteral) -> Boolean) {
        if (!predicate(literal)) {
          val description = buildErrorString(carrierKind, expected)
          val fixes = getActualKind(expected)?.let { arrayOf(GrStringTransformationFixFactory.getStringTransformationFix(it)) }
                      ?: emptyArray()
          registerError(literal, description, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
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

  private fun getActualKind(kind: InspectionStringKind): OuterStringKind? = when (kind) {
    UNDEFINED -> null
    DOUBLE_QUOTED -> OuterStringKind.DOUBLE_QUOTED
    SINGLE_QUOTED -> OuterStringKind.SINGLE_QUOTED
    SLASHY -> OuterStringKind.SLASHY
    TRIPLE_QUOTED -> OuterStringKind.TRIPLE_SINGLE_QUOTED
    TRIPLE_DOUBLE_QUOTED -> OuterStringKind.TRIPLE_DOUBLE_QUOTED
    DOLLAR_SLASHY_QUOTED -> OuterStringKind.DOLLAR_SLASHY
  }

  private fun findBestEscaping(literal: GrLiteral,
                               preferredKindInEscaping: InspectionStringKind,
                               mainKind: InspectionStringKind): Pair<InspectionStringKind, Int>? {
    val stringContent = literal.text
    val startQuote = GrStringUtil.getStartQuote(stringContent)
    val meaningfulText = GrStringUtil.unescapeString(GrStringUtil.removeQuotes(stringContent))
    val currentKind = when(startQuote) {
      GrStringUtil.QUOTE -> SINGLE_QUOTED
      GrStringUtil.DOUBLE_QUOTES -> DOUBLE_QUOTED
      GrStringUtil.TRIPLE_QUOTES -> TRIPLE_QUOTED
      GrStringUtil.TRIPLE_DOUBLE_QUOTES -> TRIPLE_DOUBLE_QUOTED
      GrStringUtil.SLASH -> SLASHY
      GrStringUtil.DOLLAR_SLASH -> DOLLAR_SLASHY_QUOTED
      else -> UNDEFINED
    }
    val doubleQuotes = meaningfulText.count { it == '"' }
    val singleQuotes = meaningfulText.count { it == '\'' }
    val dollars = meaningfulText.count { it == '$' }
    val windows = meaningfulText.windowed(3)
    val tripleQuotes = windows.count { it == GrStringUtil.TRIPLE_QUOTES }
    val tripleDoubleQuotes = windows.count { it == GrStringUtil.TRIPLE_DOUBLE_QUOTES }
    val slashes = meaningfulText.count { it == '/' }
    val reversedSlashes = meaningfulText.count { it == '\\' }
    val currentEscapingScore = when (startQuote) {
      GrStringUtil.QUOTE -> singleQuotes + reversedSlashes + dollars
      GrStringUtil.DOUBLE_QUOTES -> doubleQuotes + reversedSlashes + dollars
      GrStringUtil.TRIPLE_QUOTES -> tripleQuotes
      GrStringUtil.TRIPLE_DOUBLE_QUOTES -> tripleDoubleQuotes + dollars
      GrStringUtil.DOLLAR_SLASH -> dollars
      GrStringUtil.SLASH -> slashes
      else -> return null
    }
    val bestEscaping = listOf(DOUBLE_QUOTED to doubleQuotes + reversedSlashes + dollars,
                              SINGLE_QUOTED to singleQuotes + reversedSlashes,
                              TRIPLE_QUOTED to tripleQuotes,
                              SLASHY to slashes,
                              DOLLAR_SLASHY_QUOTED to dollars,
                              TRIPLE_DOUBLE_QUOTED to tripleDoubleQuotes + dollars)
      .map { it.first to currentEscapingScore - it.second }
      .maxWithOrNull { (kind, num), (kind2, num2) ->
        if (num == num2) {
          if (kind == mainKind) return@maxWithOrNull 1
          if (kind2 == mainKind) return@maxWithOrNull -1
          if (kind == preferredKindInEscaping) return@maxWithOrNull 1
          if (kind2 == preferredKindInEscaping) return@maxWithOrNull -1
          if (kind == currentKind) return@maxWithOrNull 1
          if (kind2 == currentKind) return@maxWithOrNull -1
          kind.toString().compareTo(kind2.toString()) // induce deterministic total order
        }
        else {
          num.compareTo(num2)
        }
      }
    return bestEscaping?.takeIf { it.first != mainKind && it.second >= 0 }
  }


}
