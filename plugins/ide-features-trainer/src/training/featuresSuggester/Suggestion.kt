package training.featuresSuggester

import org.jetbrains.annotations.Nls

sealed class Suggestion

object NoSuggestion : Suggestion()

sealed class UiSuggestion(val suggesterId: String) : Suggestion()

abstract class PopupSuggestion(
  @Nls val message: String,
  suggesterId: String
) : UiSuggestion(suggesterId)

class TipSuggestion(
  @Nls message: String,
  suggesterId: String,
  val suggestingTipId: String
) : PopupSuggestion(message, suggesterId)

class DocumentationSuggestion(
  @Nls message: String,
  suggesterId: String,
  val documentURL: String
) : PopupSuggestion(message, suggesterId)

class CustomSuggestion(
  suggesterId: String,
  val activate: () -> Unit,
): UiSuggestion(suggesterId)
