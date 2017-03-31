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

import com.intellij.execution.*
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
  override fun getConfigurationFactories() = emptyArray<ConfigurationType>()

  override fun getConfigurations(type: ConfigurationType) = emptyArray<RunConfiguration>()

  override fun getConfigurationsList(type: ConfigurationType) = emptyList<RunConfiguration>()

  override fun getAllConfigurations() = emptyArray<RunConfiguration>()

  override fun getAllConfigurationsList(): List<RunConfiguration> {
    return emptyList()
  }

  val tempConfigurations: Array<RunConfiguration>
    get() = emptyArray<RunConfiguration>()

  override fun getTempConfigurationsList(): List<RunnerAndConfigurationSettings> {
    return emptyList()
  }

  fun isTemporary(configuration: RunConfiguration): Boolean {
    return false
  }

  override fun makeStable(configuration: RunConfiguration) {}

  override fun makeStable(settings: RunnerAndConfigurationSettings) {}

  override fun getSelectedConfiguration(): RunnerAndConfigurationSettings? {
    return null
  }

  override fun createRunConfiguration(name: String, type: ConfigurationFactory): RunnerAndConfigurationSettings {
    throw UnsupportedOperationException()
  }

  override fun createConfiguration(runConfiguration: RunConfiguration, factory: ConfigurationFactory): RunnerAndConfigurationSettings {
    throw UnsupportedOperationException()
  }

  override fun getConfigurationTemplate(factory: ConfigurationFactory): RunnerAndConfigurationSettings {
    throw UnsupportedOperationException()
  }

  override fun getConfigurationSettings(type: ConfigurationType): Array<RunnerAndConfigurationSettings> {
    return emptyArray()
  }

  override fun getConfigurationSettingsList(type: ConfigurationType): List<RunnerAndConfigurationSettings> {
    return emptyList()
  }

  override fun getStructure(type: ConfigurationType): Map<String, List<RunnerAndConfigurationSettings>> {
    return emptyMap()
  }

  override fun getAllSettings(): List<RunnerAndConfigurationSettings> {
    return emptyList()
  }

  override fun setSelectedConfiguration(configuration: RunnerAndConfigurationSettings?) {}

  override fun setTemporaryConfiguration(tempConfiguration: RunnerAndConfigurationSettings?) {}

  override fun getConfig(): RunManagerConfig? {
    return null
  }

  override fun createConfiguration(name: String, type: ConfigurationFactory): RunnerAndConfigurationSettings {
    throw UnsupportedOperationException()
  }

  override fun addConfiguration(settings: RunnerAndConfigurationSettings,
                                isShared: Boolean,
                                tasks: List<BeforeRunTask<*>>,
                                addTemplateTasksIfAbsent: Boolean) {
  }

  override fun addConfiguration(settings: RunnerAndConfigurationSettings, isShared: Boolean) {}

  override fun isConfigurationShared(settings: RunnerAndConfigurationSettings): Boolean {
    return false
  }

  override fun getBeforeRunTasks(settings: RunConfiguration): List<BeforeRunTask<*>> {
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

  override fun getSortedConfigurations(): Collection<RunnerAndConfigurationSettings> {
    return emptyList()
  }

  override fun removeConfiguration(settings: RunnerAndConfigurationSettings?) {}

  override fun addRunManagerListener(listener: RunManagerListener) {}

  override fun removeRunManagerListener(listener: RunManagerListener) {}

  override fun refreshUsagesList(profile: RunProfile) {}
}
