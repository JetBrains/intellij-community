package com.intellij.settingsSync.core.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.components.SettingsCategory
import com.intellij.settingsSync.core.SettingsSyncSettings
import com.intellij.settingsSync.core.config.BUNDLED_PLUGINS_ID
import com.intellij.settingsSync.core.config.EDITOR_FONT_SUBCATEGORY_ID

class SettingsSyncStateStatistics : ApplicationUsagesCollector() {

  private val GROUP: EventLogGroup = EventLogGroup("settings.sync.state", 4)
  private val SETTINGS_SYNC_ENABLED_STATE = GROUP.registerEvent("general.state", EventFields.Boolean("enabled"))
  private val DISABLED_CATEGORIES = GROUP.registerEvent("disabled.categories", EventFields.Enum("category", SettingsCategory::class.java))
  private val BUNDLED_PLUGINS_DISABLED = GROUP.registerEvent("disabled.bundled.plugins", EventFields.Boolean("disabled"))
  private val EDITOR_FONT_STATE = GROUP.registerEvent("editor.font.state", EventFields.Boolean("enabled"))

  override fun getMetrics(): Set<MetricEvent> {
    val settings = SettingsSyncSettings.getInstance()
    val result = mutableSetOf<MetricEvent>()
    result += SETTINGS_SYNC_ENABLED_STATE.metric(settings.syncEnabled)
    for (disabledCategory in settings.state.disabledCategories) {
      result += DISABLED_CATEGORIES.metric(disabledCategory)
    }
    val disabledSubcategories = settings.state.disabledSubcategories
    if (true == disabledSubcategories[SettingsCategory.PLUGINS]?.contains(BUNDLED_PLUGINS_ID)) {
      result += BUNDLED_PLUGINS_DISABLED.metric(true)
    }
    result += EDITOR_FONT_STATE.metric(true == disabledSubcategories[SettingsCategory.UI]?.contains(EDITOR_FONT_SUBCATEGORY_ID))
    return result
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}