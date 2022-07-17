package training.featuresSuggester

import org.jetbrains.annotations.Nls

sealed class Suggestion

object NoSuggestion : Suggestion()

abstract class PopupSuggestion(
  @Nls val message: String,
  val suggesterId: String
) : Suggestion()

class TipSuggestion(
  @Nls message: String,
  suggesterId: String,
  val suggestingTipFilename: String
) : PopupSuggestion(message, suggesterId)

class DocumentationSuggestion(
  @Nls message: String,
  suggesterId: String,
  val documentURL: String
) : PopupSuggestion(message, suggesterId)
