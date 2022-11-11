package org.jetbrains.completion.full.line.python.formatters.elements

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.psi.PsiElement
import com.jetbrains.python.PyElementTypes
import com.jetbrains.python.psi.PyFromImportStatement
import com.jetbrains.python.psi.PyImportElement
import com.jetbrains.python.psi.PyImportStatementBase
import org.jetbrains.completion.full.line.language.ElementFormatter
import java.util.*

class ImportFormatter(private val dropoutRate: Double? = null) : ElementFormatter {
  private val rng = Random(0xBAD_C0DE)

  override fun condition(element: PsiElement): Boolean = element is PyImportStatementBase

  override fun filter(element: PsiElement): Boolean = element is PyImportStatementBase

  override fun format(element: PsiElement): String {
    element as PyImportStatementBase
    if (dropoutRate != null && rng.nextDouble() >= dropoutRate) return ""

    val from = presentableFrom(element)
    var imports = element.importElements.map { presentableImport(it) }.ifEmpty { listOf("") }.joinToString(", ")

    if (isStar(element)) {
      imports = "*"
    }

    return when {
      from == null -> "import $imports"
      !element.text.contains("import") -> "from $from"
      else -> "from $from import $imports"
    }.trimEnd()
  }

  private fun isStar(element: PyImportStatementBase): Boolean {
    return (element as StubBasedPsiElementBase<*>).getStubOrPsiChild(PyElementTypes.STAR_IMPORT_ELEMENT) != null
  }

  private fun presentableImport(element: PyImportElement): String {
    val asPart = if (element.asName == null) {
      ""
    }
    else {
      " as " + element.asName
    }

    return (element.importedQName?.toString() ?: "") + asPart
  }

  private fun presentableFrom(element: PyImportStatementBase): String? {
    return if (element is PyFromImportStatement) {
      ".".repeat(element.relativeLevel) + if (element.importSource == null) {
        ""
      }
      else {
        element.importSource!!.text
      }
    }
    else {
      null
    }
  }
}
