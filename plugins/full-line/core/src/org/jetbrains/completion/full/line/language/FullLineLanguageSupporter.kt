package org.jetbrains.completion.full.line.language

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.template.Template
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.completion.full.line.ReferenceCorrectness

interface FullLineLanguageSupporter {
  val language: Language
  val iconSet: IconSet
  val formatter: CodeFormatter
  val psiFormatter: PsiCodeFormatter
  val langState: LangState

  fun skipLocation(parameters: CompletionParameters): String?

  fun configure(parameters: CompletionParameters): FullLineConfiguration

  fun isLanguageSupported(language: Language): Boolean = language == this.language

  fun customizeFilePath(path: String): String = path

  /**
   * @return list of imported elements. This is optional and only needed for debugging purpose.
   */
  fun autoImportFix(file: PsiFile, editor: Editor, suggestionRange: TextRange): List<PsiElement>

  fun getFirstToken(line: String): String?

  fun createStringTemplate(element: PsiElement, range: TextRange): Template?

  fun getMissingBraces(line: String, element: PsiElement?, offset: Int): List<Char>?

  fun isCorrect(file: PsiFile, suggestion: String, offset: Int, prefix: String): ReferenceCorrectness

  fun isStringElement(element: PsiElement): Boolean

  fun isStringWalkingEnabled(element: PsiElement): Boolean

  fun createCodeFragment(file: PsiFile, text: String, isPhysical: Boolean = true): PsiFile?

  fun isSimpleElement(element: PsiElement): Boolean {
    return element is PsiWhiteSpace || element.textLength == 1
  }

  companion object {
    private val INSTANCE = ExtensionPointName<FullLineLanguageSupporter>("org.jetbrains.completion.full.line.fullLineLanguageSupport")

    fun supportedLanguages() = INSTANCE.extensionList.map { it.language }

    fun getInstance(language: Language): FullLineLanguageSupporter? {
      return INSTANCE.extensions.find { it.isLanguageSupported(language) }
    }
  }
}
