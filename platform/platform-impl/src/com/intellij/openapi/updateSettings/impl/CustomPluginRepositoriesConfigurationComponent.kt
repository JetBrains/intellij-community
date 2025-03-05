// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("UpdateSettingsProviderHelper")
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.getOpenedProjects
import com.intellij.util.xmlb.annotations.XCollection
import org.jetbrains.annotations.ApiStatus


internal class ConfigurationScriptPluginRepositoriesProvider : UpdateSettingsProvider {
  override fun getPluginRepositories(): List<String> {
    return getOpenedProjects()
      .filter { it.isTrusted() }
      .flatMap { project ->
        project.service<CustomPluginRepositoriesConfigurationComponent>().repositories
      }
      .toList()
  }
}

internal class CustomPluginRepositoriesConfigurationComponent(val project: Project) :
  SimplePersistentStateComponent<PluginsConfiguration>(PluginsConfiguration()) {

  val repositories: List<String>
    get() = state.repositories
}

@ApiStatus.Internal
class PluginsConfiguration : BaseState() {
  @get:XCollection
  val repositories: MutableList<String> by list<String>()
}
