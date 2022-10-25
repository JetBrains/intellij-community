package org.jetbrains.completion.full.line.language.supporters


import com.intellij.lang.Language
import com.intellij.lang.javascript.TypeScriptFileType
import com.intellij.lang.javascript.dialects.TypeScriptLanguageDialect
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.completion.full.line.language.*
import org.jetbrains.completion.full.line.language.formatters.DummyPsiCodeFormatter
import org.jetbrains.completion.full.line.language.formatters.TSCodeFormatter

class TSSupporter : JSDialectSupporter() {
  override val fileType: FileType = TypeScriptFileType.INSTANCE

  override val language = Language.getRegisteredLanguages().find { it.id == "TypeScript" } as TypeScriptLanguageDialect

  override val iconSet = TSIconSet()

  override val formatter = TSCodeFormatter()

  override val psiFormatter = DummyPsiCodeFormatter()

  override val langState: LangState = LangState(
    enabled = false,
    localModelState = ModelState(numIterations = 8),
    redCodePolicy = RedCodePolicy.DECORATE
  )
}
