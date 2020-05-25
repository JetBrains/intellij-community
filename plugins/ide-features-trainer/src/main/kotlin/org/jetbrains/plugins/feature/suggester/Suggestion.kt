package org.jetbrains.plugins.feature.suggester

sealed class Suggestion

object NoSuggestion : Suggestion()

object FeatureUsageSuggestion : Suggestion()

class PopupSuggestion(val message: String) : Suggestion()