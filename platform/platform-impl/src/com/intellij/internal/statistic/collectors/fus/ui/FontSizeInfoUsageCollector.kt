// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui

import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.CollectUsagesException
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator.ensureProperKey
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
      MetricEvent("UI.font.size[${ui.fontSize}]"),
      MetricEvent(ensureProperKey("UI.font.name[${ui.fontFace}]")),
      MetricEvent("Presentation.mode.font.size[${ui.presentationModeFontSize}]")
    )
    if (!scheme.isUseAppFontPreferencesInEditor) {
      usages += setOf(
        MetricEvent("Editor.font.size[${scheme.editorFontSize}]"),
        MetricEvent(ensureProperKey("Editor.font.name[${scheme.editorFontName}]"))
      )
    }
    else {
      val appPrefs = AppEditorFontOptions.getInstance().fontPreferences
      usages += setOf(
        MetricEvent("IDE.editor.font.size[${appPrefs.getSize(appPrefs.fontFamily)}]"),
        MetricEvent(ensureProperKey("IDE.editor.font.name[${appPrefs.fontFamily}]"))
      )
    }
    if (!scheme.isUseEditorFontPreferencesInConsole) {
      usages += setOf(
        MetricEvent("Console.font.size[${scheme.consoleFontSize}]"),
        MetricEvent(ensureProperKey("Console.font.name[${scheme.consoleFontName}]"))
      )
    }
    val quickDocFontSize = PropertiesComponent.getInstance().getValue("quick.doc.font.size")
    if (quickDocFontSize != null) {
      usages += setOf(
        MetricEvent("QuickDoc.font.size[$quickDocFontSize]")
      )
    }

    val lineSpacing: Float = EditorColorsManager.getInstance().globalScheme.lineSpacing

    val usageData = FeatureUsageData()
    usageData.addData("value", lineSpacing)

    usages.add(MetricEvent("editor.lineSpacing", usageData ))

    return usages
  }

  override fun getGroupId(): String {
    return "ui.fonts"
  }
}
