package com.intellij.cce.report

import com.intellij.cce.core.Session
import com.intellij.cce.workspace.storages.FeaturesStorage
import kotlinx.html.id
import kotlinx.html.span
import kotlinx.html.stream.createHTML

class ConflictResolutionReportGenerator(
  filterName: String,
  comparisonFilterName: String,
  featuresStorages: List<FeaturesStorage>,
  dirs: GeneratorDirectories,
) : BasicFileReportGenerator(filterName, comparisonFilterName, featuresStorages, dirs) {

  override val externalVariables: Map<String, String> = mapOf(
    "suggestion.indent.preserve" to "true"
  )

  override fun getSpan(session: Session?, text: String, lookupOrder: Int): String {
    val lines = text.lines()
    val colorClass = ReportColors.getColor(session, HtmlColorClasses, lookupOrder)
    return createHTML().span("session ${colorClass}") {
      id = "${session?.id} $lookupOrder"

      if (lines.size <= 1) {
        +text
      }
      else {
        +lines.dropLast(1).joinToString("\n")
        +"\n"
        span("autocomplete-items-position")
        +lines.last()
      }
    }
  }
}