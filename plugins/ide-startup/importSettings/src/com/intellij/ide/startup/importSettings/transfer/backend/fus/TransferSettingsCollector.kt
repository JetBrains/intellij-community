// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.fus

import com.intellij.ide.startup.importSettings.*
import com.intellij.ide.startup.importSettings.models.FailedIdeVersion
import com.intellij.ide.startup.importSettings.models.PatchedKeymap
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.statistics.ImportSettingsEventsCollector
import com.intellij.ide.startup.importSettings.transfer.backend.models.IdeVersion
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.mappings.VisualStudioPluginsMapping
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.validator.rules.impl.AllowedItemsResourceWeakRefStorage
import com.intellij.internal.statistic.eventLog.validator.rules.impl.LocalFileCustomValidationRule
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.util.text.nullize
import kotlin.time.Duration

object TransferSettingsCollector : CounterUsagesCollector() {

  private val logger = logger<TransferSettingsCollector>()

  private val GROUP = EventLogGroup("wizard.transfer.settings", 6)
  override fun getGroup(): EventLogGroup = GROUP

  private val ideField = EventFields.Enum<TransferableIdeId>("ide")
  private val ideVersionField = EventFields.NullableEnum<TransferableIdeVersionId>("version")
  private val featureField = EventFields.StringValidatedByCustomRule<KnownPluginValidationRule>("feature")
  private val performanceMetricTypeTypeField = EventFields.Enum<PerformanceMetricType>("type")
  private val perfEventValueField = EventFields.Long("value")
  private val selectedSectionsField = EventFields.StringList("selectedSections", TransferableSections.types)
  private val unselectedSectionsField = EventFields.StringList("unselectedSections", TransferableSections.types)
  private val timesSwitchedBetweenInstancesField = EventFields.Int("timesSwitchedBetweenInstances")

  // Common events
  private val transferSettingsShown = GROUP.registerEvent("transfer.settings.shown")
  private val transferSettingsSkipped = GROUP.registerEvent("transfer.settings.skipped")
  private val importStarted = GROUP.registerEvent("import.started", selectedSectionsField, unselectedSectionsField, timesSwitchedBetweenInstancesField)
  private val importSucceeded = GROUP.registerEvent("import.succeeded", ideField, ideVersionField)
  private val importFailed = GROUP.registerEvent("import.failed", ideField, ideVersionField)

  // Discovery events
  private val instancesOfIdeFound = GROUP.registerEvent(
    "instances.of.ide.found",
    ideField,
    ideVersionField,
    EventFields.Count
  )
  private val instancesOfIdeFailed = GROUP.registerEvent(
    "instances.of.ide.failed",
    ideField,
    EventFields.Count
  )
  private val featureDetected = GROUP.registerEvent("feature.detected", ideField, featureField)
  private val recentProjectsDetected = GROUP.registerEvent("recent.projects.detected", ideField, EventFields.Count)

  // Import events
  private val lafImported = GROUP.registerEvent("laf.imported", EventFields.Enum<TransferableLafId>("laf"))
  private val shortcutsTransferred = GROUP.registerEvent(
    "shortcuts.transferred",
    EventFields.Enum<TransferableKeymapId>("keymap"),
    EventFields.Int("added_shortcut_count"),
    EventFields.Int("removed_shortcut_count")
  )
  private val recentProjectsTransferred = GROUP.registerEvent("recent.projects.transferred", ideField, EventFields.Count)
  private val featureImported = GROUP.registerEvent("feature.imported", featureField, ideField)

  // Performance events, see RIDER-60328.
  enum class PerformanceMetricType {
    SubName, Registry, ReadSettingsFile, Total
  }

  private val performanceMeasuredEvent = GROUP.registerVarargEvent(
    "performance.measured",
    performanceMetricTypeTypeField,
    ideField,
    ideVersionField,
    perfEventValueField
  )

  fun logTransferSettingsShown() {
    transferSettingsShown.log()
  }

  @Suppress("unused") // Used in Rider
  fun logTransferSettingsSkipped() {
    transferSettingsSkipped.log()
  }

  fun logImportStarted(settings: Settings, timesSwitchedBetweenInstances: Int) {
    val selectedSections = mutableListOf<String>()
    val unselectedSections = mutableListOf<String>()

    if (settings.laf != null) {
      if (settings.preferences.laf) selectedSections.add(TransferableSections.laf) else unselectedSections.add(TransferableSections.laf)
    }
    if (settings.keymap != null) {
      if (settings.preferences.keymap) selectedSections.add(TransferableSections.keymap) else unselectedSections.add(TransferableSections.keymap)
    }
    if (settings.plugins.isNotEmpty()) {
      if (settings.preferences.plugins) selectedSections.add(TransferableSections.plugins) else unselectedSections.add(TransferableSections.plugins)
    }
    if (settings.recentProjects.isNotEmpty()) {
      if (settings.preferences.recentProjects) selectedSections.add(TransferableSections.recentProjects) else unselectedSections.add(TransferableSections.recentProjects)
    }
    if (settings.syntaxScheme != null) {
      if (settings.preferences.syntaxScheme) selectedSections.add(TransferableSections.syntaxScheme) else unselectedSections.add(TransferableSections.syntaxScheme)
    }

    importStarted.log(selectedSections, unselectedSections, timesSwitchedBetweenInstances)
  }

  fun logImportSucceeded(ideVersion: IdeVersion, settings: Settings) {
    logger.runAndLogException {
      val ide = ideVersion.transferableId
      importSucceeded.log(ide, ideVersion.transferableVersion)

      if (settings.preferences.laf) {
        settings.laf?.transferableId?.let { lafImported.log(it) }
      }

      if (settings.preferences.keymap) {
        settings.keymap?.let { keymap ->
          val patchedKeymap = keymap as? PatchedKeymap
          shortcutsTransferred.log(
            keymap.transferableId,
            patchedKeymap?.overrides?.size ?: 0,
            patchedKeymap?.removal?.size ?: 0
          )
        }
      }

      if (settings.preferences.recentProjects) {
        recentProjectsTransferred.log(ide, settings.recentProjects.size)
      }

      if (settings.preferences.plugins) {
        for (pluginId in settings.plugins.keys) {
          featureImported.log(pluginId, ide)
        }
      }
    }
  }

  fun logImportFailed(ideVersion: IdeVersion) {
    logger.runAndLogException {
      importFailed.log(ideVersion.transferableId, ideVersion.transferableVersion)
    }
  }

  fun logIdeVersionsFound(versions: List<IdeVersion>) {
    logger.runAndLogException {
      versions
        .groupBy { it.transferableId to it.transferableVersion }
        .forEach { (id, version), instances ->
          instancesOfIdeFound.log(id, version, instances.size)
        }
      ImportSettingsEventsCollector.externalIdes(versions.map { it.transferableId })
    }
  }

  fun logIdeVersionsFailed(versions: List<FailedIdeVersion>) {
    logger.runAndLogException {
      versions
        .groupBy { it.transferableId }
        .forEach { (id, instances) ->
          instancesOfIdeFailed.log(id, instances.size)
        }
    }
  }

  fun logIdeSettingsDiscovered(ideVersion: IdeVersion, settings: Settings) {
    logger.runAndLogException {
      val ide = ideVersion.transferableId
      for (pluginId in settings.plugins.keys) {
        featureDetected.log(ide, pluginId.lowercase())
      }
      recentProjectsDetected.log(ide, settings.recentProjects.size)
    }
  }

  fun logPerformanceMeasured(type: PerformanceMetricType, ide: TransferableIdeId, version: TransferableIdeVersionId?, duration: Duration) {
    logger.runAndLogException {
      performanceMeasuredEvent.log(
        EventPair(performanceMetricTypeTypeField, type),
        EventPair(ideField, ide),
        EventPair(ideVersionField, version),
        EventPair(perfEventValueField, duration.inWholeMilliseconds)
      )
    }
  }
}

class KnownPluginValidationRule : LocalFileCustomValidationRule(
  "known_plugin_id",
  object : AllowedItemsResourceWeakRefStorage(KnownPluginValidationRule::class.java, "/pluginData/known-plugins.txt") {

    override fun createValue(value: String): String? = value.nullize(true)?.trim()?.lowercase()
    override fun readItems(): Set<String?> {
      return super.readItems() + VisualStudioPluginsMapping.RESHARPER.lowercase()
    }
  }
)
