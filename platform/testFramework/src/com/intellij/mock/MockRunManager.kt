/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author gregsh
 */
class MockRunManager : RunManagerEx() {
  override fun getConfigurationType(typeName: String) = TODO("not implemented")

  override fun addConfiguration(settings: RunnerAndConfigurationSettings, isShared: Boolean) {
  }

  override fun hasSettings(settings: RunnerAndConfigurationSettings) = false

  override fun getConfigurationsList(type: ConfigurationType) = emptyList<RunConfiguration>()

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
