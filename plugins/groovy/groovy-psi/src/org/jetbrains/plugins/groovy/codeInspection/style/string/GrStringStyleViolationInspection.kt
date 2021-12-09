// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.style.string

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.layout.*
import com.intellij.util.ui.CheckBox
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor
import org.jetbrains.plugins.groovy.codeInspection.style.string.GrStringStyleViolationInspection.InspectionStringQuotationKind.*
import org.jetbrains.plugins.groovy.codeInspection.style.string.GrStringStyleViolationInspection.StringUsageKind.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.reflect.KMutableProperty
import org.jetbrains.plugins.groovy.lang.psi.util.StringKind as OuterStringKind


class GrStringStyleViolationInspection : BaseInspection() {

  internal enum class InspectionStringQuotationKind {
    UNDEFINED,
    DOUBLE_QUOTED,
    SINGLE_QUOTED,
    SLASHY,
    TRIPLE_QUOTED,
    TRIPLE_DOUBLE_QUOTED,
    DOLLAR_SLASHY_QUOTED;

    @Nls
    fun representation(): String = when (this) {
        UNDEFINED -> GroovyBundle.message("string.option.do.not.handle.specifically")
        DOUBLE_QUOTED -> GroovyBundle.message("string.option.double.quoted.string")
        SINGLE_QUOTED -> GroovyBundle.message("string.option.single.quoted.string")
        SLASHY -> GroovyBundle.message("string.option.slashy.string")
        TRIPLE_QUOTED -> GroovyBundle.message("string.option.triple.quoted.string")
        TRIPLE_DOUBLE_QUOTED -> GroovyBundle.message("string.option.triple.double.quoted.string")
        DOLLAR_SLASHY_QUOTED -> GroovyBundle.message("string.option.dollar.slashy.string")
    }
  }

  companion object {
    private val PLAIN_STRING_OPTIONS = arrayOf(DOUBLE_QUOTED, SINGLE_QUOTED, SLASHY, TRIPLE_QUOTED, TRIPLE_DOUBLE_QUOTED, DOLLAR_SLASHY_QUOTED)
    private val MULTILINE_STRING_OPTIONS = arrayOf(TRIPLE_QUOTED, SLASHY, TRIPLE_DOUBLE_QUOTED, DOLLAR_SLASHY_QUOTED)
    private val ESCAPED_STRING_OPTIONS = arrayOf(DOUBLE_QUOTED, SINGLE_QUOTED, SLASHY, TRIPLE_QUOTED, TRIPLE_DOUBLE_QUOTED, DOLLAR_SLASHY_QUOTED)
    private val INTERPOLATED_STRING_OPTIONS = arrayOf(DOUBLE_QUOTED, SLASHY, TRIPLE_DOUBLE_QUOTED, DOLLAR_SLASHY_QUOTED)

    private fun JPanel.addStringKindComboBox(@Nls description: String,
                                             field: KMutableProperty<InspectionStringQuotationKind>,
                                             values: Array<InspectionStringQuotationKind>,
                                             id: Int,
                                             constraints: GridBagConstraints) {
      constraints.gridy = id
      constraints.gridx = 0
      add(JLabel(description), constraints)
      val comboBox: JComboBox<InspectionStringQuotationKind> = ComboBox(values).apply {
        renderer = SimpleListCellRenderer.create("") { it.representation() }
        selectedItem = field.getter.call()
        addItemListener { e ->
          val selectedItem = e.item
          if (field.getter.call() != selectedItem) {
            field.setter.call(selectedItem)
          }
        }
      }
      constraints.gridx = 1
      add(comboBox, constraints)
    }

    private fun getActualKind(kind: InspectionStringQuotationKind): OuterStringKind? = when (kind) {
      UNDEFINED -> null
      DOUBLE_QUOTED -> OuterStringKind.DOUBLE_QUOTED
      SINGLE_QUOTED -> OuterStringKind.SINGLE_QUOTED
      SLASHY -> OuterStringKind.SLASHY
      TRIPLE_QUOTED -> OuterStringKind.TRIPLE_SINGLE_QUOTED
      TRIPLE_DOUBLE_QUOTED -> OuterStringKind.TRIPLE_DOUBLE_QUOTED
      DOLLAR_SLASHY_QUOTED -> OuterStringKind.DOLLAR_SLASHY
    }
  }

  @Volatile
  internal var plainStringQuotation = SINGLE_QUOTED

  @Volatile
  internal var escapedStringQuotation = UNDEFINED

  @Volatile
  internal var interpolatedStringQuotation = UNDEFINED

  @Volatile
  internal var multilineStringQuotation = TRIPLE_QUOTED

  @Volatile
  internal var inspectGradle: Boolean = true

  private fun generateComboBoxes(): JPanel = JPanel(GridBagLayout()).apply {
    val constraints = GridBagConstraints().apply {
      weightx = 1.0; weighty = 1.0; fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.WEST
    }
    addStringKindComboBox(GroovyBundle.message("string.sort.default"), ::plainStringQuotation, PLAIN_STRING_OPTIONS, 0, constraints)
    addStringKindComboBox(GroovyBundle.message("string.sort.strings.with.escaping"), ::escapedStringQuotation,
                          arrayOf(UNDEFINED, *ESCAPED_STRING_OPTIONS), 1, constraints)
    addStringKindComboBox(GroovyBundle.message("string.sort.strings.with.interpolation"), ::interpolatedStringQuotation,
                          arrayOf(UNDEFINED, *INTERPOLATED_STRING_OPTIONS), 2, constraints)
    addStringKindComboBox(GroovyBundle.message("string.sort.multiline.string"), ::multilineStringQuotation,
                          arrayOf(UNDEFINED, *MULTILINE_STRING_OPTIONS), 3, constraints)
  }

  override fun createGroovyOptionsPanel(): JComponent {
    return panel {
      titledRow(GroovyBundle.message("separator.preferable.string.kind")) {
        row { generateComboBoxes()() }
      }
      titledRow(GroovyBundle.message("separator.domain.of.inspection.usage")) {
        row { CheckBox(GroovyBundle.message("checkbox.inspect.gradle.files"), this@GrStringStyleViolationInspection, "inspectGradle")() }
      }
    }
  }

  private enum class StringUsageKind {
    PLAIN_STRING, MULTILINE_STRING, ESCAPED_STRING, INTERPOLATED_STRING
  }


  override fun buildErrorString(vararg args: Any?): String {
    val targetQuotationKind = args[1] as InspectionStringQuotationKind
    return when (args[0] as StringUsageKind) {
      PLAIN_STRING -> when (targetQuotationKind) {
        DOUBLE_QUOTED -> GroovyBundle.message("inspection.message.plain.string.should.be.double.quoted")
        SINGLE_QUOTED -> GroovyBundle.message("inspection.message.plain.string.should.be.single.quoted")
        SLASHY -> GroovyBundle.message("inspection.message.plain.string.should.be.slashy.quoted")
        DOLLAR_SLASHY_QUOTED -> GroovyBundle.message("inspection.message.plain.string.should.be.dollar.slashy.quoted")
        TRIPLE_QUOTED -> GroovyBundle.message("inspection.message.plain.string.should.be.quoted.with.triple.quotes")
        TRIPLE_DOUBLE_QUOTED -> GroovyBundle.message("inspection.message.plain.string.should.be.quoted.with.triple.double.quotes")
        else -> error("Unexpected error message")
      }
      MULTILINE_STRING -> when (targetQuotationKind) {
        TRIPLE_QUOTED -> GroovyBundle.message("inspection.message.multiline.string.should.be.quoted.with.triple.quotes")
        TRIPLE_DOUBLE_QUOTED -> GroovyBundle.message("inspection.message.multiline.string.should.be.quoted.with.triple.double.quotes")
        SLASHY -> GroovyBundle.message("inspection.message.multiline.string.should.be.slashy.quoted")
        DOLLAR_SLASHY_QUOTED -> GroovyBundle.message("inspection.message.multiline.string.should.be.dollar.slashy.quoted")
        else -> error("Unexpected error message")
      }
      ESCAPED_STRING -> GroovyBundle.message("inspection.message.string.escaping.could.be.minimized")
      INTERPOLATED_STRING -> when (targetQuotationKind) {
        DOUBLE_QUOTED -> GroovyBundle.message("inspection.message.interpolated.string.should.be.double.quoted")
        DOLLAR_SLASHY_QUOTED -> GroovyBundle.message("inspection.message.interpolated.string.should.be.dollar.slashy.quoted")
        SLASHY -> GroovyBundle.message("inspection.message.interpolated.string.should.be.slashy.quoted")
        TRIPLE_DOUBLE_QUOTED -> GroovyBundle.message("inspection.message.interpolated.string.should.be.quoted.with.triple.double.quotes")
        else -> error("Unexpected error message")
      }
    }
  }

  override fun buildVisitor(): BaseInspectionVisitor = object : BaseInspectionVisitor() {
    override fun visitLiteralExpression(literal: GrLiteral) {
      if (!inspectGradle && literal.containingFile.name.endsWith("gradle")) {
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
      checkInconsistency(interpolatedStringQuotation, literal, INTERPOLATED_STRING, INTERPOLATED_STRING_OPTIONS)
    }

    private fun handlePlainString(literal: GrLiteral) {
      val literalText = literal.text
      if (multilineStringQuotation != UNDEFINED && GrStringUtil.isMultilineStringLiteral(literal) && literalText.contains("\n")) {
        checkInconsistency(multilineStringQuotation, literal, MULTILINE_STRING, MULTILINE_STRING_OPTIONS)
        return
      }
      else if (escapedStringQuotation != UNDEFINED) {
        val bestEscaping = findBestQuotationForEscaping(literalText, escapedStringQuotation, plainStringQuotation)
        if (bestEscaping != null) {
          if (bestEscaping.second != 0) {
            checkInconsistency(bestEscaping.first, literal, ESCAPED_STRING, ESCAPED_STRING_OPTIONS)
          }
          return
        }
      }
      if ("\n" !in literalText) {
        checkInconsistency(plainStringQuotation, literal, PLAIN_STRING, PLAIN_STRING_OPTIONS)
      }
    }

    private fun checkInconsistency(expected: InspectionStringQuotationKind,
                                   literal: GrLiteral,
                                   usageKind: StringUsageKind,
                                   availableStringKinds: Array<InspectionStringQuotationKind>) {
      if (expected !in availableStringKinds) {
        return
      }
      fun doCheck(predicate: (GrLiteral) -> Boolean) {
        if (!predicate(literal)) {
          val description = buildErrorString(usageKind, expected)
          val fixes = getActualKind(expected)?.let { arrayOf(getStringTransformationFix(it)) } ?: emptyArray()
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

}
