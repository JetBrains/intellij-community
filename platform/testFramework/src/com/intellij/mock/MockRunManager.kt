// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.RunManagerConfig
import com.intellij.execution.RunManagerEx
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.util.Key
import javax.swing.Icon

class MockRunManager : RunManagerEx() {
  override fun findSettings(configuration: RunConfiguration): RunnerAndConfigurationSettings? = null

  override fun getConfigurationType(typeName: String): Nothing = TODO("not implemented")

  override fun hasSettings(settings: RunnerAndConfigurationSettings): Boolean = false

  override fun getConfigurationsList(type: ConfigurationType): List<RunConfiguration> = emptyList<RunConfiguration>()

  override fun makeStable(settings: RunnerAndConfigurationSettings) {}

  override val configurationFactories: Array<ConfigurationType>
    get() = emptyArray()

  override val configurationFactoriesWithoutUnknown: List<ConfigurationType>
    get() = emptyList()

  override val allConfigurationsList: List<RunConfiguration>
    get() = emptyList()

  override val allSettings: List<RunnerAndConfigurationSettings>
    get() = emptyList()

  override val tempConfigurationsList: List<RunnerAndConfigurationSettings>
    get() = emptyList()

  override var selectedConfiguration: RunnerAndConfigurationSettings?
    get() = null
    set(value) {}

  override fun createConfiguration(runConfiguration: RunConfiguration, factory: ConfigurationFactory): RunnerAndConfigurationSettings {
    throw UnsupportedOperationException()
  }

  override fun getConfigurationTemplate(factory: ConfigurationFactory): RunnerAndConfigurationSettings {
    throw UnsupportedOperationException()
  }

  override fun getConfigurationSettingsList(type: ConfigurationType): List<RunnerAndConfigurationSettings> {
    return emptyList()
  }

  override fun getStructure(type: ConfigurationType): Map<String, List<RunnerAndConfigurationSettings>> {
    return emptyMap()
  }

  override fun setTemporaryConfiguration(tempConfiguration: RunnerAndConfigurationSettings?) {}

  override fun getConfig(): RunManagerConfig {
    throw UnsupportedOperationException()
  }

  override fun createConfiguration(name: String, factory: ConfigurationFactory): RunnerAndConfigurationSettings {
    throw UnsupportedOperationException()
  }

  override fun addConfiguration(settings: RunnerAndConfigurationSettings) {
  }

  override fun getBeforeRunTasks(configuration: RunConfiguration): List<BeforeRunTask<*>> {
    return emptyList()
  }

  override fun <T : BeforeRunTask<*>> getBeforeRunTasks(taskProviderID: Key<T>): List<T> {
    return emptyList()
  }

  override fun <T : BeforeRunTask<*>> getBeforeRunTasks(settings: RunConfiguration, taskProviderID: Key<T>): List<T> {
    return emptyList()
  }

  override fun setBeforeRunTasks(runConfiguration: RunConfiguration, tasks: List<BeforeRunTask<*>>, addEnabledTemplateTasksIfAbsent: Boolean) {}

  override fun findConfigurationByName(name: String?): RunnerAndConfigurationSettings? {
    return null
  }

  override fun getConfigurationIcon(settings: RunnerAndConfigurationSettings): Icon? {
    return null
  }

  override fun getConfigurationIcon(settings: RunnerAndConfigurationSettings, withLiveIndicator: Boolean): Icon? {
    return null
  }

  override fun removeConfiguration(settings: RunnerAndConfigurationSettings?) {}

  override fun refreshUsagesList(profile: RunProfile) {}
}
