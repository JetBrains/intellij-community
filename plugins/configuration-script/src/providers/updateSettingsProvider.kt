package com.intellij.configurationScript.providers

import com.intellij.configurationScript.ConfigurationFileManager
import com.intellij.configurationScript.readIntoObject
import com.intellij.configurationScript.schemaGenerators.PluginJsonSchemaGenerator
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.processOpenedProjects
import com.intellij.openapi.updateSettings.impl.UpdateSettingsProvider
import com.intellij.openapi.util.NotNullLazyKey
import com.intellij.util.SmartList
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.xmlb.annotations.XCollection

private val dataKey = NotNullLazyKey.create<SynchronizedClearableLazy<PluginsConfiguration?>, Project>("MyUpdateSettingsProvider") { project ->
  val data = SynchronizedClearableLazy {
    val node = ConfigurationFileManager.getInstance(project).findValueNode(PluginJsonSchemaGenerator.plugins) ?: return@SynchronizedClearableLazy null
    readIntoObject(PluginsConfiguration(), node)
  }

  ConfigurationFileManager.getInstance(project).registerClearableLazyValue(data)
  data
}

private class MyUpdateSettingsProvider : UpdateSettingsProvider {
  override fun getPluginRepositories(): List<String> {
    val result = SmartList<String>()
    processOpenedProjects { project ->
      dataKey.getValue(project).value?.repositories?.let {
        result.addAll(it)
      }
    }
    return result
  }
}

internal class PluginsConfiguration : BaseState() {
  @get:XCollection
  val repositories by list<String>()
}