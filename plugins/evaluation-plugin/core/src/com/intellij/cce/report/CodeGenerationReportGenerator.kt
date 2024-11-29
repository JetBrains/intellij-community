// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.report

import com.intellij.cce.core.Session
import com.intellij.cce.evaluable.AIA_HAS_SYNTAX_ERRORS
import com.intellij.cce.workspace.storages.FeaturesStorage
import kotlinx.html.id
import kotlinx.html.span
import kotlinx.html.stream.createHTML

class CodeGenerationReportGenerator(
  filterName: String,
  comparisonFilterName: String,
  featuresStorages: List<FeaturesStorage>,
  dirs: GeneratorDirectories,
  private val positionBasedColors: Boolean = true
) : BasicFileReportGenerator(filterName, comparisonFilterName, featuresStorages, dirs) {

  override val scripts: List<Resource> = listOf(Resource("/diff.js", "../res/diff.js")) + super.scripts

  override fun textToInsert(session: Session) = session.expectedText.lines().first()

  override fun getSpan(session: Session?, text: String, lookupOrder: Int): String =
    createHTML().span("session ${
      getColor(session, lookupOrder)
    } code-generation") {
      id = "${session?.id} $lookupOrder"
      +text
    }

  private fun getColor(session: Session?, lookupOrder: Int): String {
    if (session == null || session.lookups.size <= lookupOrder) return HtmlColorClasses.notFoundColor
    val lookup = session.lookups[lookupOrder]

    if (positionBasedColors) {
      return if (lookup.selectedPosition == -1) HtmlColorClasses.notFoundColor else HtmlColorClasses.perfectSortingColor
    }
    else {
      return if (lookup.additionalInfo.getOrDefault(AIA_HAS_SYNTAX_ERRORS, true) as Boolean) HtmlColorClasses.notFoundColor
      else HtmlColorClasses.perfectSortingColor
    }
  }
}
