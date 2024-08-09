// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.statistics

import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.chooser.productChooser.ExpChooserAction
import com.intellij.ide.startup.importSettings.chooser.productChooser.JbChooserAction
import com.intellij.ide.startup.importSettings.chooser.productChooser.OtherOptions
import com.intellij.ide.startup.importSettings.jb.IDEData
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.components.SettingsCategory


object ImportSettingsEventsCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("import.settings", 5)
  override fun getGroup(): EventLogGroup = GROUP
  private val UNKNOWN = "UNKNOWN"
  private val FOLDER = "FOLDER"

  // Lists/enums:
  private val ALLOWED_JB_IDES: List<String> = IDEData.IDE_MAP.keys.plus(UNKNOWN).toList()
  private val CATEGORIES: List<SettingsCategory> = SettingsCategory.entries.minus(SettingsCategory.OTHER).toList()

  // items supporting 'multiple' (with configure/showAll link)
  private val ITEMS_MULTIPLE_IDS = listOf(
    com.intellij.ide.startup.importSettings.transfer.TransferableSetting.UI_ID,
    com.intellij.ide.startup.importSettings.transfer.TransferableSetting.KEYMAP_ID,
    com.intellij.ide.startup.importSettings.transfer.TransferableSetting.PLUGINS_ID,
    com.intellij.ide.startup.importSettings.transfer.TransferableSetting.RECENT_PROJECTS_ID,
    SettingsCategory.PLUGINS.name
  )

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

  private enum class ProductPageDropdown {
    SYNC,
    JB,
    EXTERNAL,
    OTHER
  }

  private enum class ProductPageButton {
    SYNC,
    SYNC_OLD,
    JB,
    JB_OLD,
    EXTERNAL,
    FOLDER,
    SKIP,
    CLOSE,
  }

  enum class ConfigurePageButton {
    NEXT,
    BACK,
    CLOSE
  }

  enum class ImportErrorTypes {
    CONNECTION_ERROR,
  }


  private val JB_IDE_VALUES = EventFields.StringList("jbIdeValues", ALLOWED_JB_IDES, "Supported JB IDEs")
  private val EXTERNAL_IDE_VALUES = EventFields.EnumList<TransferableIdeId>("externalIdeValues", "Supported external IDEs")
  private val FIRST_PAGE_BUTTONS = EventFields.Enum<ProductPageButton>("productPageButton", "Buttons on the first page")
  private val SECOND_PAGE_BUTTONS = EventFields.Enum<ConfigurePageButton>("configurePageButton", "Buttons on the second page")
  private val IMPORT_TYPES = EventFields.Enum<ImportType>("importTypes", "Import type")
  private val PLUGIN_CANT_IMPORT_REASONS = EventFields.Enum<ImportErrorTypes>("importErrorTypes")
  private val JB_IMPORT_CATEGORIES = EventFields.StringList("settingsCategories",
                                                            CATEGORIES.map { it.name },
                                                            "Settings categories when importing from JB or SYNC")

  private val IMPORT_SOURCE = EventFields.String("importSource",
                                                 ALLOWED_JB_IDES
                                                   .plus("FOLDER")
                                                   .plus(TransferableIdeId.entries.map { it.name })
  )

  // before first page - preparations and performance

  // first page (product) - select import source or skip
  private val productPageShown = GROUP.registerEvent("page.product.shown", EventFields.Boolean("shown"),
                                                     "indicates whether initial import settings page was shown to user, if not, then import was skipped completely")
  private val jbIdeActualValues = GROUP.registerEvent("jb.ide.actual.values", JB_IDE_VALUES, "JB IDEs in the main dropdown")
  private val jbIdeOldValues = GROUP.registerEvent("jb.ide.old.values", JB_IDE_VALUES, "JB IDEs in the other dropdown")
  private val externalIdeValues = GROUP.registerEvent("external.ide.values", EXTERNAL_IDE_VALUES, "external IDEs available for import")
  private val productPageButton = GROUP.registerEvent("page.product.button", FIRST_PAGE_BUTTONS, "Button pressed on the product page")
  private val jbIdeSelectedValue = GROUP.registerEvent("page.product.selected.jb.ide", EventFields.String("jbIde", ALLOWED_JB_IDES), "JB IDE selected")
  private val externalIdeSelectedValue = GROUP.registerEvent("external.ide.selected.value", EventFields.Enum<TransferableIdeId>("externalIde"), "External IDE selected")
  private val productPageTimeSpent = GROUP.registerEvent("page.product.time.spent", EventFields.DurationMs)

  private val productPageDropdownClicked = GROUP.registerEvent("page.product.dropdown.clicked",
                                                               EventFields.Enum<ProductPageDropdown>("dropdownId"),
                                                               "User clicked to the JB IDEs dropdown")

  //second page (configure) - JB IDE - select import details
  private val configurePageShown = GROUP.registerEvent("page.configure.shown", IMPORT_TYPES)
  private val jbIdeDisabledOptions = GROUP.registerEvent("page.configure.jb.disabled.categories",
                                                         JB_IMPORT_CATEGORIES,
                                                         "unselected options when importing from JB IDE")
  private val jbIdePlugins = GROUP.registerEvent(
    "page.configure.jb.ide.plugins",
    EventFields.Int("totalCount", "Total number of plugins that we've found during scanning"),
    EventFields.Int("unselectedCount", "number of unselected plugins"),
    "number of plugins and number of unselected plugins")
  private val configurePageExpandClicked = GROUP.registerEvent("page.configure.expand.clicked",
                                                               EventFields.String("itemId", ITEMS_MULTIPLE_IDS),
                                                               "User clicked on configure/show all link")
  private val configurePageButton = GROUP.registerEvent("page.configure.button", SECOND_PAGE_BUTTONS, "Button pressed on the second page")
  private val configurePageTimeSpent = GROUP.registerEvent("page.configure.time.spent", EventFields.DurationMs)

  private val featuredPluginsPageShown = GROUP.registerEvent("page.featured.plugins.shown",
                                                    "Indicates whether the Featured Plugins page was shown. Only for certain products (Rider) that include it.")

  // third page - (import) progress dialog
  private val importPageShown = GROUP.registerEvent("page.import.shown",
                                                    "Indicates whether the third page (import progress dialog) was shown. It's common for all import types")
  private val importPageClosed = GROUP.registerEvent("page.import.closed",
                                                     "Indicates whether the third page (import progress dialog) was closed manually via button. " +
                                                     "That typically indicates a problem, because user doesn't want to wait for the import to finish")
  private val importType = GROUP.registerEvent("import.type",
                                               IMPORT_TYPES,
                                               IMPORT_SOURCE,
                                               "Which type of import is used (JB/NONJB/SYNC) and the source name")
  private val pluginsImportTime = GROUP.registerEvent("import.plugins.time.spent", EventFields.DurationMs, "How long did it take to import plugins")
  private val pluginsCounts = GROUP.registerEvent("import.plugins.counts",
                                                  EventFields.Long("imported"),
                                                  EventFields.Long("skipped"),
                                                  "How many plugins were imported during imported or skipped")
  private val jbPluginImportType = GROUP.registerEvent("import.plugins.import.type", EventFields.Boolean("isNew"), "What plugin import type is used (new or legacy)")
  private val jbPluginCantImport = GROUP.registerEvent("import.plugins.cant.import.reason", PLUGIN_CANT_IMPORT_REASONS)
  private val optionsImportTime = GROUP.registerEvent("import.options.time.spent", EventFields.DurationMs, "How long did it take to import options and schemas")
  private val totalImportTime = GROUP.registerEvent("import.total.time.spent", EventFields.DurationMs, "how long did it take to import everything")

  // after restart, but before showing welcome screen - reload last settings
  private val afterImportRestartTime = GROUP.registerEvent("after.import.restart.time", EventFields.DurationMs, "How long did it take to restart")


  /////// Methods

  private fun changePage(pageIdx: Int) {
    val currentTime = System.currentTimeMillis()
    if (currentPageIdx == 1) {
      productPageTimeSpent.log(currentTime - currentPageShownTime)
    }
    else if (currentPageIdx == 2) {
      configurePageTimeSpent.log(currentTime - currentPageShownTime)
    }
    currentPageShownTime = currentTime
    currentPageIdx = pageIdx
  }

  fun productPageShown() {
    productPageShown.log(true)
    changePage(1)
  }

  fun importDialogNotShown() {
    productPageShown.log(false)
  }

  fun productPageSkipButton() {
    productPageButton.log(ProductPageButton.SKIP)
  }

  fun productPageDropdownClicked(caller: Any) {
    if (caller is OtherOptions) {
      productPageDropdownClicked.log(ProductPageDropdown.OTHER)
    }
    else if (caller is JbChooserAction) {
      productPageDropdownClicked.log(ProductPageDropdown.JB)
    }
    else if (caller is ExpChooserAction) {
      productPageDropdownClicked.log(ProductPageDropdown.EXTERNAL)
    }
  }

  fun dialogClosed() {
    when (currentPageIdx) {
      1 -> {
        productPageButton.log(ProductPageButton.CLOSE)
      }
      2 -> {
        configurePageButton.log(ConfigurePageButton.CLOSE)
      }
      3 -> {
        importPageClosed.log()
      }
      else -> {
        // do nothing
      }
    }
  }

  fun configurePageBack() {
    configurePageButton.log(ConfigurePageButton.BACK)
  }

  fun configurePageExpandClicked(itemId: String) {
    configurePageExpandClicked.log(itemId)
  }

  fun configurePageImportSettingsClicked() {
    configurePageButton.log(ConfigurePageButton.NEXT)
  }

  fun externalIdes(extIdes: List<TransferableIdeId>) {
    externalIdeValues.log(extIdes)
  }

  fun actualJbIdes(jbIdes: List<String>) {
    jbIdeActualValues.log(jbIdes)
  }

  fun oldJbIdes(jbIdes: List<String>) {
    jbIdeOldValues.log(jbIdes)
  }

  fun featuredPluginsPageShown() {
    featuredPluginsPageShown.log()
  }

  fun importProgressPageShown() {
    importPageShown.log()
  }

  fun jbIdeSelected(jbIde: String, isActual: Boolean) {
    productPageButton.log(if (isActual) ProductPageButton.JB else ProductPageButton.JB_OLD)
    configurePageShown.log(ImportType.JB)
    changePage(2)
    if (IDEData.IDE_MAP.keys.contains(jbIde))
      jbIdeSelectedValue.log(jbIde)
    else
      jbIdeSelectedValue.log(UNKNOWN)
  }

  fun externalSelected(externalId: TransferableIdeId) {
    productPageButton.log(ProductPageButton.EXTERNAL)
    configurePageShown.log(ImportType.EXTERNAL)
    externalIdeSelectedValue.log(externalId)
    changePage(2)
  }

  fun customDirectorySelected() {
    productPageButton.log(ProductPageButton.FOLDER)
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
    jbIdeDisabledOptions.log(unselectedCategories.map { it.name })
    val totalPluginsCount = selectedPlugins.size + unselectedPlugins.size
    jbIdePlugins.log(totalPluginsCount, unselectedPlugins.size)
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

  fun importFinished() {
    changePage(0)
  }
}