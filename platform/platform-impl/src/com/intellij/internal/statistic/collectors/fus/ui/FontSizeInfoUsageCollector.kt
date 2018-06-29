// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui

import com.intellij.ide.ui.UISettings
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.CollectUsagesException
import com.intellij.internal.statistic.beans.UsageDescriptor
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.UsageDescriptorKeyValidator.ensureProperKey
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions

/**
 * @author Konstantin Bulenkov
 */
class FontSizeInfoUsageCollector : ApplicationUsagesCollector() {
  @Throws(CollectUsagesException::class)
  override fun getUsages(): Set<UsageDescriptor> {
    val scheme = EditorColorsManager.getInstance().globalScheme
    val ui = UISettings.shadowInstance
    var usages = setOf(
      UsageDescriptor("UI.font.size[${ui.fontSize}]"),
      UsageDescriptor(ensureProperKey("UI.font.name[${ui.fontFace}]")),
      UsageDescriptor("Presentation.mode.font.size[${ui.presentationModeFontSize}]")
    )
    if (!scheme.isUseAppFontPreferencesInEditor) {
      usages += setOf(
        UsageDescriptor("Editor.font.size[${scheme.editorFontSize}]"),
        UsageDescriptor(ensureProperKey("Editor.font.name[${scheme.editorFontName}]"))
      )
    }
    else {
      val appPrefs = AppEditorFontOptions.getInstance().fontPreferences
      usages += setOf(
        UsageDescriptor("IDE.editor.font.size[${appPrefs.getSize(appPrefs.fontFamily)}]"),
        UsageDescriptor(ensureProperKey("IDE.editor.font.name[${appPrefs.fontFamily}]"))
      )
    }
    if (!scheme.isUseEditorFontPreferencesInConsole) {
      usages += setOf(
        UsageDescriptor("Console.font.size[${scheme.consoleFontSize}]"),
        UsageDescriptor(ensureProperKey("Console.font.name[${scheme.consoleFontName}]"))
      )
    }
    val quickDocFontSize = PropertiesComponent.getInstance().getValue("quick.doc.font.size")
    if (quickDocFontSize != null) {
      usages += setOf(
        UsageDescriptor("QuickDoc.font.size[" + quickDocFontSize +"]")
      )
    }
    return usages
  }

  override fun getGroupId(): String {
    return "statistics.ui.fonts"
  }
}
