package org.jetbrains.plugins.feature.suggester

sealed class Suggestion

object NoSuggestion : Suggestion()

abstract class PopupSuggestion(
    val message: String,
    val suggesterId: String
) : Suggestion()

class TipSuggestion(
    message: String,
    suggesterId: String,
    val suggestingTipFilename: String
) : PopupSuggestion(message, suggesterId)

class DocumentationSuggestion(
    message: String,
    suggesterId: String,
    val documentURL: String
) : PopupSuggestion(message, suggesterId)
