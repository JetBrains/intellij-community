package org.jetbrains.completion.full.line.platform

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiFile
import org.jetbrains.completion.full.line.language.FullLineConfiguration
import org.jetbrains.completion.full.line.language.FullLineLanguageSupporter
import org.jetbrains.completion.full.line.projectFilePath
import org.jetbrains.completion.full.line.settings.state.MLServerCompletionSettings

sealed class FullLineRequest {
  data class Inapplicable(val reason: String) : FullLineRequest()

  class Applicable(private val parameters: CompletionParameters, val supporter: FullLineLanguageSupporter) :
    FullLineRequest() {

    val file: PsiFile = parameters.originalFile

    val language: Language = supporter.language

    val config: FullLineConfiguration = configure(supporter, parameters)

    private val psiBased = MLServerCompletionSettings.getInstance().getModelState(language).psiBased

    fun createQuery(prefix: String): FullLineCompletionQuery = when (psiBased) {
      false -> createTextBasedCompletionQuery(prefix)
      true -> createPsiBasedCompletionQuery(prefix)
    }

    private fun createTextBasedCompletionQuery(prefix: String): FullLineCompletionQuery {
      val filename = supporter.customizeFilePath(file.projectFilePath())
      val context = supporter.formatter.format(parameters.originalFile, TextRange(0, parameters.offset), parameters.editor)
      val offset = context.length
      return FullLineCompletionQuery(config.mode, context, filename, prefix, offset, supporter.language, file.project, emptyList())
    }

    private fun createPsiBasedCompletionQuery(prefix: String): FullLineCompletionQuery {
      val filename = supporter.customizeFilePath(file.projectFilePath())
      val formatResult = supporter.psiFormatter.format(parameters.position.containingFile, parameters.position, parameters.offset)
      val context = formatResult.context
      val offset = context.length
      val rollbackPrefix = formatResult.rollbackPrefix
      return FullLineCompletionQuery(config.mode, context, filename, prefix, offset, supporter.language, file.project, rollbackPrefix)
    }
  }

  companion object {
    fun of(parameters: CompletionParameters): FullLineRequest {
      return FullLineCompletionParameters(parameters).buildRequest()
    }

    private fun configure(supporter: FullLineLanguageSupporter, parameters: CompletionParameters): FullLineConfiguration {
      if (Registry.`is`("full.line.multi.token.everywhere")) {
        return FullLineConfiguration.Line
      }

      return supporter.configure(parameters)
    }
  }

  private class FullLineCompletionParameters(private val parameters: CompletionParameters) {

    fun buildRequest(): FullLineRequest {
      val language = parameters.language()
      val supporter: FullLineLanguageSupporter = FullLineLanguageSupporter.getInstance(language)
                                                 ?: return Inapplicable("Language \"${language.displayName}\" is not supported.")
      val reason = whySkip() ?: supporter.skipLocation(parameters)
      return if (reason != null) {
        Inapplicable(reason)
      }
      else {
        Applicable(parameters, supporter)
      }
    }

    private fun whySkip(): String? {
      val settings = MLServerCompletionSettings.getInstance()
      val language = parameters.language()

      if (!settings.isEnabled(language)) return "Full line disabled for \"${language.displayName}\" language"
      if (settings.isGreyTextMode()) return "Grey text mode is enabled"

      if (!ApplicationManager.getApplication().isUnitTestMode) {
        if (!isHeadless() && !isInMainEditor()) return "Editor is not a main one"
        if (!isBasicCompletion()) return "Full line available only for Basic completion"
      }

      return null
    }

    private fun isHeadless() = ApplicationManager.getApplication().isHeadlessEnvironment
    private fun isInMainEditor() = parameters.editor.editorKind == EditorKind.MAIN_EDITOR
    private fun isBasicCompletion() = parameters.completionType == CompletionType.BASIC

    private fun CompletionParameters.language() = originalFile.language
  }
}


