// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.ui

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions

/**
 * @author Konstantin Bulenkov
 */
class FontSizeInfoUsageCollector : ApplicationUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("ui.fonts", 6)

    val FONT_NAME = EventFields.String(
      "font_name", arrayListOf(
      "Monospaced", "Menlo", "DejaVu_Sans_Mono", ".SFNSText-Regular", "Fira_Code", "Lucida_Grande", "Source_Code_Pro", "Segoe_UI", "Ubuntu",
      ".SF_NS_Text", "Consolas", "Noto_Sans_Regular", "Microsoft_YaHei", "Fira_Code_Retina", "Cantarell_Regular", "Microsoft_YaHei_UI",
      "Monaco", "Noto_Sans", "Dialog.plain", "Fira_Code_Medium", "Courier_New", "Tahoma", "Hack", "DejaVu_Sans", "Ubuntu_Mono",
      "Droid_Sans_Mono", "Dialog", "Inconsolata", "Malgun_Gothic", "Cantarell", "DialogInput", "Yu_Gothic_UI_Regular", "Roboto",
      "Liberation_Mono", "Lucida_Console", "D2Coding", "Lucida_Sans_Typewriter", "Fira_Code_Light", "Droid_Sans", "Verdana", "Arial",
      "Roboto_Mono", "Segoe_UI_Semibold", "SF_Mono", "Droid_Sans_Mono_Slashed", "LucidaGrande", "Operator_Mono", "Ayuthaya", "Hasklig",
      "Iosevka", "Andale_Mono", "Anonymous_Pro", "Anonymous_Pro_for_Powerline", "D2Coding_ligature", "Dank_Mono",
      "DejaVu_Sans_Mono_for_Powerline", "Fantasque_Sans_Mono", "Fira_Mono_for_Powerline", "Hack_Nerd_Font", "IBM_Plex_Mono",
      "Meslo_LG_L_DZ_for_Powerline", "Meslo_LG_M_for_Powerline", "Meslo_LG_S_for_Powerline", "Microsoft_YaHei_Mono",
      "Noto_Mono_for_Powerline", "Noto_Sans_Mono", "PT_Mono", "PragmataPro", "SourceCodePro+Powerline+Awesome_Regular",
      "Source_Code_Pro_Semibold", "Source_Code_Pro_for_Powerline", "Ubuntu_Mono_derivative_Powerline", "YaHei_Consolas_Hybrid",
      "mononoki", "Bitstream_Vera_Sans_Mono", "Comic_Sans_MS", "Courier_10_Pitch", "Cousine", "2Coding_ligature", "Droid_Sans_Mono_Dotted",
      "Inconsolata-dz", "Input", "Input_Mono", "Meslo_LG_M_DZ_for_Powerline", "Migu_2M", "Monoid", "Operator_Mono_Book",
      "Operator_Mono_Lig", "Operator_Mono_Medium", "Abadi_MT_Condensed_Extra_Bold", "Al_Bayan", "Meiryo", "Microsoft_JhengHei",
      "Microsoft_Yahei_UI", "SansSerif", "Ubuntu_Light", "JetBrains_Mono", ".AppleSystemUIFont", ".SFNS-Regular", "Inter"
    ))

    val FONT_SIZE = EventFields.Int("font_size")
    val FONT_SIZE_2D = EventFields.Float("font_size_2d")
    val LINE_SPACING = EventFields.Float("line_spacing")
    private val FONT_SIZE_STRING = EventFields.String(
      "font_size", arrayListOf("X_SMALL", "X_LARGE", "XX_SMALL", "XX_LARGE", "SMALL", "MEDIUM", "LARGE")
    )

    val UI_FONT = GROUP.registerEvent("UI", FONT_NAME, FONT_SIZE, FONT_SIZE_2D)
    val PRESENTATION_MODE_FONT = GROUP.registerEvent("Presentation.mode", FONT_SIZE)
    val EDITOR_FONT = GROUP.registerVarargEvent("Editor", FONT_NAME, FONT_SIZE, FONT_SIZE_2D, LINE_SPACING)
    val IDE_EDITOR_FONT = GROUP.registerVarargEvent("IDE.editor", FONT_NAME, FONT_SIZE, FONT_SIZE_2D, LINE_SPACING)
    val CONSOLE_FONT = GROUP.registerVarargEvent("Console", FONT_NAME, FONT_SIZE, FONT_SIZE_2D, LINE_SPACING)
    val QUICK_DOC_FONT = GROUP.registerEvent("QuickDoc", FONT_SIZE_STRING)
  }

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics(): Set<MetricEvent> {
    val scheme = EditorColorsManager.getInstance().globalScheme
    val ui = UISettings.shadowInstance
    val usages = mutableSetOf(
      UI_FONT.metric(ui.fontFace, ui.fontSize, ui.fontSize2D),
      PRESENTATION_MODE_FONT.metric(UISettingsUtils.with(ui).presentationModeFontSize.toInt())
    )
    if (!scheme.isUseAppFontPreferencesInEditor) {
      usages += EDITOR_FONT.metric(
        FONT_NAME.with(scheme.editorFontName),
        FONT_SIZE.with(scheme.editorFontSize),
        FONT_SIZE_2D.with(scheme.editorFontSize2D),
        LINE_SPACING.with(scheme.lineSpacing))
    }
    else {
      val appPrefs = AppEditorFontOptions.getInstance().fontPreferences
      usages += IDE_EDITOR_FONT.metric(
        FONT_NAME.with(appPrefs.fontFamily),
        FONT_SIZE.with(appPrefs.getSize(appPrefs.fontFamily)),
        FONT_SIZE_2D.with(appPrefs.getSize2D(appPrefs.fontFamily)),
        LINE_SPACING.with(appPrefs.lineSpacing))
    }
    if (!scheme.isUseEditorFontPreferencesInConsole) {
      usages += CONSOLE_FONT.metric(
        FONT_NAME.with(scheme.consoleFontName),
        FONT_SIZE.with(scheme.consoleFontSize),
        FONT_SIZE_2D.with(scheme.consoleFontSize2D),
        LINE_SPACING.with(scheme.consoleLineSpacing))
    }
    val quickDocFontSize = PropertiesComponent.getInstance().getValue("quick.doc.font.size.v3")
    if (quickDocFontSize != null) {
      usages += QUICK_DOC_FONT.metric(quickDocFontSize)
    }
    return usages
  }
}
