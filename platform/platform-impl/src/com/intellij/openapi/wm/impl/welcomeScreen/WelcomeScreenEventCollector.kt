// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.wm.WelcomeScreenTab
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object WelcomeScreenEventCollector : CounterUsagesCollector() {
  internal enum class TabType { TabNavProject, TabNavCustomize, TabNavPlugins, TabNavTutorials, TabNavOther }

  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("welcomescreen.interaction", 3)

  private val shown = GROUP.registerEvent("screen.shown", EventFields.Boolean("first_start"), EventFields.Boolean("config_imported"))

  private val hide = GROUP.registerEvent("screen.hidden")
  private val tabSelected = GROUP.registerEvent("screen.tab.selected", EventFields.Enum("tab_type", TabType::class.java))

  private val projectSearchUsed = GROUP.registerEvent("project.search")

  private val lafChanged = GROUP.registerEvent("laf.changed", EventFields.StringValidatedByEnum("theme_name", "look_and_feel"),
                                               EventFields.Boolean("sync_os"))

  private val OLD_FONT_SIZE = EventFields.Int("old_font_size")
  private val NEW_FONT_SIZE = EventFields.Int("new_font_size")
  private val OLD_FONT_SIZE_2D = EventFields.Float("old_font_size_2d")
  private val NEW_FONT_SIZE_2D = EventFields.Float("new_font_size_2d")
  private val ideFontChanged = GROUP.registerVarargEvent("ide.font.changed",
                                                         OLD_FONT_SIZE, NEW_FONT_SIZE, OLD_FONT_SIZE_2D, NEW_FONT_SIZE_2D)
  private val editorFontChanged = GROUP.registerVarargEvent("editor.font.changed",
                                                            OLD_FONT_SIZE, NEW_FONT_SIZE, OLD_FONT_SIZE_2D, NEW_FONT_SIZE_2D)
  private val colorBlindnessChanged = GROUP.registerEvent("color.blindness.changed", EventFields.Boolean("enabled"))
  private val keymapChanged = GROUP.registerEvent("keymap.changed", EventFields.StringValidatedByEnum("keymap_name", "keymaps"))
  private val pluginsModified = GROUP.registerEvent("plugins.modified")

  internal val debuggerTabProcessesSearchUsed: EventId = GROUP.registerEvent("debugger.processes.search")
  internal val debuggerAttachUsed: EventId = GROUP.registerEvent("debugger.attach")

  @JvmStatic
  fun logWelcomeScreenShown(): Unit = shown.log(ConfigImportHelper.isFirstSession(), ConfigImportHelper.isConfigImported())

  @JvmStatic
  fun logWelcomeScreenHide(): Unit = hide.log()

  @JvmStatic
  fun logTabSelected(selectedTab: WelcomeScreenTab): Unit = tabSelected.log(
    (selectedTab as? TabbedWelcomeScreen.DefaultWelcomeScreenTab)?.type ?: TabType.TabNavOther)

  @JvmStatic
  fun logProjectSearchUsed(): Unit = projectSearchUsed.log()

  fun logLafChanged(laf: UIThemeLookAndFeelInfo, osSync: Boolean): Unit = lafChanged.log(laf.name, osSync)

  fun logIdeFontChanged(oldSize: Float, newSize: Float): Unit = ideFontChanged.log(
    OLD_FONT_SIZE.with((oldSize + 0.5).toInt()), NEW_FONT_SIZE.with((newSize + 0.5).toInt()),
    OLD_FONT_SIZE_2D.with(oldSize), NEW_FONT_SIZE_2D.with(newSize))

  fun logEditorFontChanged(oldSize: Float, newSize: Float): Unit = editorFontChanged.log(
    OLD_FONT_SIZE.with((oldSize + 0.5).toInt()), NEW_FONT_SIZE.with((newSize + 0.5).toInt()),
    OLD_FONT_SIZE_2D.with(oldSize), NEW_FONT_SIZE_2D.with(newSize))

  fun logColorBlindnessChanged(enabled: Boolean): Unit = colorBlindnessChanged.log(enabled)

  fun logKeymapChanged(keymap: Keymap): Unit = keymapChanged.log(keymap.name)

  @JvmStatic
  fun logPluginsModified(): Unit = pluginsModified.log()
}
