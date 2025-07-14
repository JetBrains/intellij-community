package com.intellij.cce.evaluation.data

/**
 * Represents data for displaying colored insights in code.
 * This is used to show model insights, positive examples, and negative examples with different colors.
 *
 * @param filePath The path to the file containing the insights
 * @param text The text with HTML color tags for insights
 */
data class ColoredInsightsData(
  val filePath: String,
  val text: String,
) : HasDescription {
  override val descriptionText: String = filePath
}