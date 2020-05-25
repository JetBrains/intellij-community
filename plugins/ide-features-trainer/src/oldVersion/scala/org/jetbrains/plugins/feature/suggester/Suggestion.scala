package org.jetbrains.plugins.feature.suggester

/**
 * @author Alefas
 * @since 23.05.13
 */
sealed trait Suggestion

case object NoSuggestion extends Suggestion

case object FeatureUsageSuggestion extends Suggestion

case class PopupSuggestion(message: String) extends Suggestion
