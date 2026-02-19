package com.intellij.cce.filter.impl

import com.google.gson.JsonArray
import com.intellij.cce.core.CaretPosition
import com.intellij.cce.core.CodeToken
import com.intellij.cce.core.Language
import com.intellij.cce.filter.EvaluationFilter
import com.intellij.cce.filter.EvaluationFilterConfiguration

class PositionFilter(private val acceptedPositions: List<CaretPosition>) : EvaluationFilter {
  override fun shouldEvaluate(code: CodeToken): Boolean {
    val position = code.properties
      .additionalProperty(POSITION_PROPERTY)
      ?.let { CaretPosition.valueOf(it) } ?: return false
    return position in acceptedPositions
  }

  override fun toJson() = JsonArray(acceptedPositions.size)
    .apply { acceptedPositions.forEach { it.name } }

  companion object {
    const val POSITION_PROPERTY = "position"
  }
}

class PositionFilterConfiguration : EvaluationFilterConfiguration {
  override val id: String = "position"
  override val description: String = "Filter out session if cursor is in a different position"
  override val hasUI: Boolean = false

  override fun isLanguageSupported(languageName: String) = Language.entries.any { it.displayName == languageName }

  override fun buildFromJson(json: Any?) = if (json == null) EvaluationFilter.ACCEPT_ALL else {
    @Suppress("UNCHECKED_CAST")
    PositionFilter((json as List<String>).map { CaretPosition.valueOf(it) })
  }

  override fun defaultFilter() = EvaluationFilter.ACCEPT_ALL
}