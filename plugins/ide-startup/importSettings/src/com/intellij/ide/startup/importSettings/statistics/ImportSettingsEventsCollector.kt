// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.statistics

import com.intellij.ide.startup.importSettings.jb.IDEData
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.components.SettingsCategory


object ImportSettingsEventsCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("import.settings.events", 1)
  override fun getGroup(): EventLogGroup = GROUP
  private val UNKNOWN = "UNKNOWN"
  private val FOLDER = "FOLDER"

  // Lists/enums:
  private val ALLOWED_JB_IDES: List<String> = IDEData.IDE_MAP.keys.plus(UNKNOWN).toList()
  private val CATEGORIES: List<SettingsCategory> = SettingsCategory.entries.minus(SettingsCategory.OTHER).toList()

  // 1 - select product
  // 2 - customize
  // 3 - import progress
  private var currentPageIdx: Int = 0
  private var currentPageShownTime: Long = 0

  private enum class ImportType {
    JB,
    JB_RAW, // means that it would just copy the old folder to the new one.
    EXTERNAL,
    SYNC,
    FOLDER,
  }

  private enum class FirstPageButton {
    SYNC,
    SYNC_OLD,
    JB,
    JB_OLD,
    EXTERNAL,
    FOLDER,
    SKIP,
    CLOSE,
  }

  enum class SecondPageButton {
    NEXT,
    BACK,
    CLOSE
  }

  enum class ImportErrorTypes {
    CONNECTION_ERROR,
  }

  enum class CloseDialogActions {

  }

  private val JB_IDE_VALUES = EventFields.StringList("jbIdeValues", ALLOWED_JB_IDES, "Supported JB IDEs")
  private val FIRST_PAGE_BUTTONS = EventFields.Enum<FirstPageButton>("firstPageButton", "Buttons on the first page")
  private val SECOND_PAGE_BUTTONS = EventFields.Enum<SecondPageButton>("secondPageButton", "Buttons on the second page")
  private val IMPORT_TYPES = EventFields.Enum<ImportType>("importTypes", "Import type")
  private val PLUGIN_CANT_IMPORT_REASONS = EventFields.Enum<ImportErrorTypes>("importErrorTypes")
  private val JB_IMPORT_CATEGORIES = EventFields.StringList("settingsCategories",
                                                            CATEGORIES.map { it.name },
                                                            "Settings categories when importing from JB or SYNC")

  private val IMPORT_SOURCE = EventFields.String("importSource", ALLOWED_JB_IDES.plus("FOLDER"))
  private val TIME = EventFields.Long("timeMs")

  // before first page - preparations and performance

  // first page - select import source or skip
  private val firstPageShown = GROUP.registerEvent("first.page.shown", EventFields.Boolean("shown"),
                                                   "indicates whether initial import settings page was shown to user, if not, then import was skipped completely")
  private val jbIdeActualValues = GROUP.registerEvent("jb.ide.actual.values", JB_IDE_VALUES, "JB IDEs in the main dropdown")
  private val jbIdeOldValues = GROUP.registerEvent("jb.ide.old.values", JB_IDE_VALUES, "JB IDEs in the other dropdown")
  private val firstPageButton = GROUP.registerEvent("first.page.button", FIRST_PAGE_BUTTONS, "Button pressed on the first page")
  private val jbIdeSelectedValue = GROUP.registerEvent("first.page.selected.jb.ide", EventFields.String("jbIde", ALLOWED_JB_IDES), "JB IDE selected")
  private val firstPageTimeSpent = GROUP.registerEvent("first.page.time.spent", TIME)

  //second page - JB IDE - select import details
  private val secondPageShown = GROUP.registerEvent("second.page.shown", IMPORT_TYPES)
  private val jbIdeUnselectedOptions = GROUP.registerEvent("second.page.jb.categories",
                                                           JB_IMPORT_CATEGORIES,
                                                           "unselected options when importing from JB IDE")
  private val jbIdePlugins = GROUP.registerEvent(
    "second.page.jb.ide.plugins",
    EventFields.Int("totalCount", "Total number of plugins that we've found during scanning"),
    EventFields.Int("unselectedCount", "number of unselected plugins"),
    "number of plugins and number of unselected plugins")
  private val jbConfigurePluginsClicked = GROUP.registerEvent("second.page.jb.configure.plugins.clicked", "User clicked on configure plugins link for JB IDE")
  private val secondPageJbButton = GROUP.registerEvent("second.page.jb.button", SECOND_PAGE_BUTTONS, "Button pressed on the second JB page")
  private val secondPageTimeSpent = GROUP.registerEvent("second.page.time.spent", TIME)

  // third page - progress dialog
  private val thirdPageShown = GROUP.registerEvent("third.page.shown", EventFields.Boolean("shown"),
                                                   "Indicates whether the third page (import progress dialog) was shown. It's common for all import types")
  private val importType = GROUP.registerEvent("import.type",
                                               IMPORT_TYPES,
                                               IMPORT_SOURCE,
                                               "Which type of import is used (JB/NONJB/SYNC) and the source name")
  private val pluginsImportTime = GROUP.registerEvent("import.plugins.time.spent", TIME, "How long did it take to import plugins")
  private val pluginsCounts = GROUP.registerEvent("import.plugins.counts",
                                                  EventFields.Long("imported"),
                                                  EventFields.Long("skipped"),
                                                  "How many plugins were imported during imported or skipped")
  private val jbPluginImportType = GROUP.registerEvent("import.plugins.import.type", EventFields.Boolean("isNew"), "What plugin import type is used (new or legacy)")
  private val jbPluginCantImport = GROUP.registerEvent("import.plugins.cant.import.reason", PLUGIN_CANT_IMPORT_REASONS)
  private val optionsImportTime = GROUP.registerEvent("import.options.time.spent", TIME, "How long did it take to import options and schemas")
  private val totalImportTime = GROUP.registerEvent("import.total.time.spent", TIME, "how long did it take to import everything")


  // after restart, but before showing welcome screen - reload last settings
  private val afterImportRestartTime = GROUP.registerEvent("after.import.restart.time", TIME, "How long did it take to restart")


  /////// Methods

  private fun changePage(pageIdx: Int) {
    val currentTime = System.currentTimeMillis()
    if (currentPageIdx == 1) {
      firstPageTimeSpent.log(currentTime - currentPageShownTime)
    }
    else if (currentPageIdx == 2) {
      secondPageTimeSpent.log(currentTime - currentPageShownTime)
    }
    currentPageShownTime = currentTime
    currentPageIdx = pageIdx
  }

  fun firstPageShown() {
    firstPageShown.log(true)
    changePage(1)
  }

  fun firstPageSkipped() {
    firstPageShown.log(false)
  }

  fun actualJbIdes(jbIdes: List<String>) {
    jbIdeActualValues.log(jbIdes)
  }

  fun oldJbIdes(jbIdes: List<String>) {
    jbIdeOldValues.log(jbIdes)
  }


  fun jbIdeSelected(jbIde: String, isActual: Boolean) {
    firstPageButton.log(if (isActual) FirstPageButton.JB else FirstPageButton.JB_OLD)
    secondPageShown.log(ImportType.JB)
    changePage(2)
    if (IDEData.IDE_MAP.keys.contains(jbIde))
      jbIdeSelectedValue.log(jbIde)
    else
      jbIdeSelectedValue.log(UNKNOWN)
  }

  fun externalSelected(externalId: String) {
    firstPageButton.log(FirstPageButton.EXTERNAL)
    secondPageShown.log(ImportType.EXTERNAL)
    changePage(2)
  }

  fun customDirectorySelected() {
    firstPageButton.log(FirstPageButton.FOLDER)
    importType.log(ImportType.FOLDER, FOLDER)
    changePage(0)
  }

  fun jbRawSelected(jbIde: String) {
    importType.log(ImportType.JB_RAW, jbIde)
    changePage(3)
  }

  fun jbImportStarted(jbIde: String,
                      selectedCategories: Collection<SettingsCategory>,
                      selectedPlugins: List<String>,
                      unselectedPlugins: List<String>
  ) {
    importType.log(ImportType.JB, jbIde)
    changePage(3)
    val unselectedCategories = CATEGORIES.minus(selectedCategories.toSet())
    jbIdeUnselectedOptions.log(unselectedCategories.map { it.name })
    val totalPluginsCount = selectedPlugins.size + unselectedPlugins.size
    jbIdePlugins.log(totalPluginsCount, unselectedPlugins.size)
  }

  fun firstPageTimeSpent(timeMs: Long) {
    firstPageTimeSpent.log(timeMs)
  }

  fun secondPageTimeSpent(timeMs: Long) {
    secondPageTimeSpent.log(timeMs)
  }

  fun jbPluginImportConnectionError() {
    jbPluginCantImport.log(ImportErrorTypes.CONNECTION_ERROR)
  }

  fun jbPluginsOldImport() {
    jbPluginImportType.log(false)
  }

  fun jbPluginsNewImport() {
    jbPluginImportType.log(true)
  }

  fun jbPluginsImportTimeSpent(timeMs: Long) {
    pluginsImportTime.log(timeMs)
  }

  fun jbOptionsImportTimeSpent(timeMs: Long) {
    optionsImportTime.log(timeMs)
  }

  fun jbTotalImportTimeSpent(timeMs: Long) {
    totalImportTime.log(timeMs)
  }

  fun importDialogClosed() {
    changePage(0)
  }
}