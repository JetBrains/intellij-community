package org.jetbrains.completion.full.line.python.supporters

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.template.Template
import com.intellij.codeInspection.InspectionManager
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.refactoring.suggested.startOffset
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.codeInsight.imports.AutoImportQuickFix
import com.jetbrains.python.inspections.unresolvedReference.PyUnresolvedReferencesInspection
import com.jetbrains.python.psi.PyFormattedStringElement
import com.jetbrains.python.psi.PyStringElement
import com.jetbrains.python.psi.PyStringLiteralExpression
import com.jetbrains.python.psi.impl.PyExpressionCodeFragmentImpl
import com.jetbrains.python.psi.impl.references.PyImportReference
import com.jetbrains.python.psi.impl.references.PyOperatorReference
import org.jetbrains.completion.full.line.language.FullLineConfiguration
import org.jetbrains.completion.full.line.language.LangState
import org.jetbrains.completion.full.line.language.LocationMatcher
import org.jetbrains.completion.full.line.language.RedCodePolicy
import org.jetbrains.completion.full.line.language.formatters.DummyPsiCodeFormatter
import org.jetbrains.completion.full.line.language.supporters.FullLineLanguageSupporterBase
import org.jetbrains.completion.full.line.python.PythonIconSet
import org.jetbrains.completion.full.line.python.formatters.PythonCodeFormatter

class PythonSupporter : FullLineLanguageSupporterBase(listOf(PythonLocationMatcher)) {
  override val fileType: FileType = PythonFileType.INSTANCE

  override val language: Language = PythonLanguage.INSTANCE

  private val additionalLanguages = listOfNotNull(
    "JupyterPython",
    "Jupyter"
  )

  private val jupyterExtensionRegex = Regex("ipynb$")

  override val iconSet = PythonIconSet()

  override val formatter = PythonCodeFormatter()

  override val psiFormatter = DummyPsiCodeFormatter()

  override val langState: LangState = LangState(redCodePolicy = RedCodePolicy.DECORATE)

  override val modelVersion = "0.0.8"

  override fun isLanguageSupported(language: Language): Boolean {
    return super.isLanguageSupported(language) || additionalLanguages.contains(language.id)
  }

  override fun customizeFilePath(path: String): String {
    return if (path.endsWith(fileType.defaultExtension)) {
      path
    }
    else {
      jupyterExtensionRegex.replace(path, fileType.defaultExtension)
    }
  }

  override fun autoImportFix(file: PsiFile, editor: Editor, suggestionRange: TextRange): List<PsiElement> {
    val problems = PyUnresolvedReferencesInspection.getInstance(file)
                     ?.processFile(file, InspectionManager.getInstance(editor.project))
                     ?.takeIf { it.isNotEmpty() }
                   ?: return emptyList()

    return problems.filter { suggestionRange.contains(it.startElement.startOffset) }
      .mapNotNull { problem ->
        val fixes = problem.fixes?.filterIsInstance<AutoImportQuickFix>()

        if (fixes != null && fixes.isNotEmpty()) {
          fixes.first()
            .takeIf { fix -> fix.nameToImport.length > 1 }
            ?.applyFix().let {
              problem.psiElement
            }
        }
        else {
          null
        }
      }
  }

  override fun createStringTemplate(element: PsiElement, range: TextRange): Template? {
    var content = range.substring(element.text)
    var contentOffset = 0

    return SyntaxTraverser.psiTraverser()
      .withRoot(element)
      .onRange(range)
      .filter { it is PyStringElement }
      .asIterable()
      .mapIndexedNotNull { id, it ->
        if (!range.contains(it.textRange)) {
          return null
        }
        if (it is PyStringElement && !it.isTripleQuoted) {
          val contentRange = it.contentRange.shiftRight(it.startOffset - range.startOffset + contentOffset)
          val name = "\$__Variable${id}\$"

          contentOffset += name.length - contentRange.length

          content = contentRange.replace(content, name)
          it.content
        }
        else {
          ""
        }
      }.let {
        createTemplate(content, it)
      }
  }

  override fun getMissingBraces(line: String, element: PsiElement?, offset: Int): List<Char>? {
    return if (line.endsWith("\"\"\"")) null else super.getMissingBraces(line, element, offset)
  }

  override fun containsReference(element: PsiElement, range: TextRange): Boolean {
    // Check if elements does not go beyond the boundaries
    return range.contains(element.textRange)
           // Disable checking in strings, as it can search for path elements
           && element !is PyStringLiteralExpression
           // Disable checking in operators, as it always fails
           && element.reference !is PyOperatorReference
           // Disable checking in imports, as it can be installed after
           && element.reference !is PyImportReference
           // Disable checking for file, since it's in only memory virtual file
           && element.text != "__file__"
           // Find all references and psi errors
           && element.reference != null
  }

  override fun createCodeFragment(file: PsiFile, text: String, isPhysical: Boolean): PsiFile {
    // Creating with .ipynb extension calls `unexpected file type`
    return PyExpressionCodeFragmentImpl(file.project, FileUtil.getNameWithoutExtension(file.name) + ".py", text, isPhysical)
  }

  override fun isStringElement(element: PsiElement): Boolean {
    return element is PyStringElement || element is PyStringLiteralExpression || element.parent is PyFormattedStringElement
  }

  override fun isStringWalkingEnabled(element: PsiElement): Boolean {
    return when (element) {
      is PyStringElement -> !element.isTripleQuoted
      is PyStringLiteralExpression -> !element.isDocString
      else -> true
    }
  }

  private object PythonLocationMatcher : LocationMatcher {
    override fun tryMatch(parameters: CompletionParameters): FullLineConfiguration {
      return FullLineConfiguration.Line
    }
  }
}
