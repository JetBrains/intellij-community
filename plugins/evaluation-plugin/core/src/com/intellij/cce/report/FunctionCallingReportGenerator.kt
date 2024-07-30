// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.core.Session
import com.intellij.cce.workspace.storages.FeaturesStorage
import kotlinx.html.id
import kotlinx.html.span
import kotlinx.html.stream.createHTML

class FunctionCallingReportGenerator(
  filterName: String,
  comparisonFilterName: String,
  featuresStorages: List<FeaturesStorage>,
  dirs: GeneratorDirectories
) : BasicFileReportGenerator(filterName, comparisonFilterName, featuresStorages, dirs) {

  override val scripts: List<Resource> = listOf(Resource("/chat.js", "../res/chat.js")) + super.scripts

  override fun getSpan(session: Session?, text: String, lookupOrder: Int): String =
    createHTML().span("session ${
      getColor(session, lookupOrder)
    } chat") {
      id = "${session?.id} $lookupOrder"
      +text
    }

  private fun getColor(session: Session?, lookupOrder: Int): String {
    if (session == null || session.lookups.size <= lookupOrder) return HtmlColorClasses.notFoundColor
    val lookup = session.lookups[lookupOrder]
    return when {
      lookup.suggestions.size == 1 && lookup.suggestions.first().isRelevant -> HtmlColorClasses.perfectSortingColor
      lookup.suggestions.any { it.isRelevant } -> HtmlColorClasses.goodSortingColor
      else -> HtmlColorClasses.notFoundColor
    }
  }
}
