// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui

import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.CollectUsagesException
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.newMetric
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions

/**
 * @author Konstantin Bulenkov
 */
class FontSizeInfoUsageCollector : ApplicationUsagesCollector() {
  @Throws(CollectUsagesException::class)
  override fun getMetrics(): Set<MetricEvent> {
    val scheme = EditorColorsManager.getInstance().globalScheme
    val ui = UISettings.shadowInstance
    val usages = mutableSetOf(
      newFontMetric("UI", ui.fontFace, ui.fontSize, null),
      newFontMetric("Presentation.mode", null, ui.presentationModeFontSize, null)
    )
    if (!scheme.isUseAppFontPreferencesInEditor) {
      usages += newFontMetric("Editor", scheme.editorFontName, scheme.editorFontSize, scheme.lineSpacing)
    }
    else {
      val appPrefs = AppEditorFontOptions.getInstance().fontPreferences
      usages += newFontMetric("IDE.editor", appPrefs.fontFamily, appPrefs.getSize(appPrefs.fontFamily), appPrefs.lineSpacing)
    }
    if (!scheme.isUseEditorFontPreferencesInConsole) {
      usages += newFontMetric("Console", scheme.consoleFontName, scheme.consoleFontSize, scheme.consoleLineSpacing)
    }
    val quickDocFontSize = PropertiesComponent.getInstance().getValue("quick.doc.font.size.v3")
    if (quickDocFontSize != null) {
      usages += newMetric("QuickDoc", FeatureUsageData().addData("font_size", quickDocFontSize))
    }

    return usages
  }

  private fun newFontMetric(metricId: String, fontName: String?, size: Int?, lineSpacing: Float?): MetricEvent {
    val data = FeatureUsageData()
    fontName?.let { data.addData("font_name", it) }
    size?.let { data.addData("font_size", it) }
    lineSpacing?.let { data.addData("line_spacing", it) }
    return MetricEvent(metricId, data)
  }

  override fun getGroupId(): String {
    return "ui.fonts"
  }

  override fun getVersion(): Int = 3
}
