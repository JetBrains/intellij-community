package org.jetbrains.completion.full.line.language.supporters

import com.intellij.lang.Language
import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.lang.javascript.JavascriptLanguage
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.completion.full.line.language.*
import org.jetbrains.completion.full.line.language.formatters.DummyPsiCodeFormatter
import org.jetbrains.completion.full.line.language.formatters.JSCodeFormatter

class JSSupporter : JSDialectSupporter() {
  override val fileType: FileType = JavaScriptFileType.INSTANCE

  override val language = JavascriptLanguage.INSTANCE

  override val iconSet = JSIconSet()

  override val formatter = JSCodeFormatter()

  override val psiFormatter = DummyPsiCodeFormatter()

  override val langState: LangState = LangState(
    enabled = false,
    localModelState = ModelState(numIterations = 8),
    redCodePolicy = RedCodePolicy.DECORATE,
  )

  private val additionalLanguages = listOfNotNull(
    "ECMAScript 6",
    "JavaScript 1.8",
    "JavaScript 1.5"
  )

  override fun isLanguageSupported(language: Language): Boolean {
    return super.isLanguageSupported(language) || additionalLanguages.contains(language.id)
  }
}
