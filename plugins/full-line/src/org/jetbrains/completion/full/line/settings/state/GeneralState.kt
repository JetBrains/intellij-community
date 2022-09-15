package org.jetbrains.completion.full.line.settings.state

import org.jetbrains.completion.full.line.language.CheckboxFlag
import org.jetbrains.completion.full.line.language.FullLineLanguageSupporter
import org.jetbrains.completion.full.line.language.IgnoreInInference
import org.jetbrains.completion.full.line.language.LangState
import org.jetbrains.completion.full.line.models.ModelType

data class GeneralState(
  var enable: Boolean = false,

  @CheckboxFlag
  var useTopN: Boolean = true,
  var topN: Int = 3,
  var useGrayText: Boolean = false,
  var modelType: ModelType = ModelType.Local,

  // Language-specific settings (i.e. things that depend on model, server, language)
  @IgnoreInInference
  var langStates: HashMap<String, LangState> =
    MLServerCompletionSettings.availableLanguages
      .map { it.id to (FullLineLanguageSupporter.getInstance(it)?.langState?.copy() ?: LangState()) }
      .toMap(HashMap())
)
