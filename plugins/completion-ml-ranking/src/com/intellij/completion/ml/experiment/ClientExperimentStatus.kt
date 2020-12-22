// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.experiment

import com.google.gson.Gson
import com.intellij.ide.util.PropertiesComponent
import com.intellij.internal.statistic.eventLog.EventLogConfiguration
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry

class ClientExperimentStatus : ExperimentStatus {
  companion object {
    private const val EXPERIMENT_DISABLED_PROPERTY_KEY = "ml.completion.experiment.disabled"
    private const val PATH_TO_EXPERIMENT_CONFIG = "/experiment.json"
    private val GSON by lazy { Gson() }
    private val LOG = logger<ClientExperimentStatus>()

    fun loadExperimentInfo(): ExperimentConfig {
      try{
        if (!ApplicationManager.getApplication().isEAP) {
          return ExperimentConfig.disabledExperiment()
        }
        val json = ClientExperimentStatus::class.java.getResource(PATH_TO_EXPERIMENT_CONFIG).readText()
        val experimentInfo = GSON.fromJson(json, ExperimentConfig::class.java)
        checkExperimentGroups(experimentInfo)
        return experimentInfo
      }
      catch (e: Throwable) {
        LOG.error("Error on loading ML Completion experiment info", e)
        return ExperimentConfig.disabledExperiment()
      }
    }

    private fun checkExperimentGroups(experimentInfo: ExperimentConfig) {
      for (group in experimentInfo.groups) {
        if (group.showArrows) assert(group.useMLRanking) { "Showing arrows requires ML ranking" }
        if (group.useMLRanking) assert(group.calculateFeatures) { "ML ranking requires calculating features" }
      }
      for (language in experimentInfo.languages) {
        assert(language.includeGroups.size <= language.experimentBucketsCount)
        { "Groups count must be less than the total number of buckets (${language.id})" }
        assert(language.includeGroups.all { number ->
          experimentInfo.groups.any { it.number == number }
        }) { "Group included for language (${language.id}) should be among general list of groups" }
      }
    }
  }

  private val experimentConfig: ExperimentConfig = loadExperimentInfo()
  private val language2group: MutableMap<String, ExperimentInfo> = mutableMapOf()

  init {
    for (languageSettings in experimentConfig.languages) {
      val bucket = EventLogConfiguration.bucket % languageSettings.experimentBucketsCount
      val groupNumber = if (languageSettings.includeGroups.size > bucket) languageSettings.includeGroups[bucket] else experimentConfig.version
      val group = experimentConfig.groups.find { it.number == groupNumber }
      val groupInfo = if (group == null) ExperimentInfo(false, experimentConfig.version)
      else ExperimentInfo(true, group.number, group.useMLRanking, group.showArrows, group.calculateFeatures)
      language2group[languageSettings.id] = groupInfo
    }
  }

  override fun forLanguage(language: Language): ExperimentInfo {
    if (ApplicationManager.getApplication().isUnitTestMode) return ExperimentInfo(false, 0)
    val matchingLanguage = findMatchingLanguage(language) ?: return ExperimentInfo(
      false, experimentConfig.version)
    val experimentGroupRegistry = Registry.get("completion.ml.override.experiment.group.number")
    if (experimentGroupRegistry.isChangedFromDefault) {
      val group = experimentConfig.groups.find { it.number == experimentGroupRegistry.asInteger() }
      if (group != null) {
        setDisabled(false)
        return ExperimentInfo(true, group.number, group.useMLRanking, group.showArrows, group.calculateFeatures)
      }
    }
    return language2group[matchingLanguage] ?: ExperimentInfo(false, experimentConfig.version)
  }

  override fun disable() {
    if (ApplicationManager.getApplication().isEAP) {
      setDisabled(true)
    }
  }

  override fun isDisabled(): Boolean = PropertiesComponent.getInstance().isTrueValue(EXPERIMENT_DISABLED_PROPERTY_KEY)

  private fun setDisabled(value: Boolean) = PropertiesComponent.getInstance().setValue(EXPERIMENT_DISABLED_PROPERTY_KEY, value)

  private fun findMatchingLanguage(language: Language): String? {
    val baseLanguages = Language.getRegisteredLanguages().filter { language.isKindOf(it) }
    return language2group.keys.find { languageId ->
      baseLanguages.any { languageId.equals(it.id, ignoreCase = true) }
    }
  }
}