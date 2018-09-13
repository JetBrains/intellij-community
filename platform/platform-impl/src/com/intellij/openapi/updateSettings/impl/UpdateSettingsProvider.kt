// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("UpdateSettingsProviderHelper")
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.ProjectManager
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<UpdateOptions>()

@ApiStatus.Experimental
private val UPDATE_SETTINGS_PROVIDER_EP = ProjectExtensionPointName<UpdateSettingsProvider>("com.intellij.updateSettingsProvider")

@ApiStatus.Experimental
interface UpdateSettingsProvider {
  fun getPluginRepositories(): List<String>
}

internal fun addPluginRepositories(to: MutableList<String>) {
  for (project in ProjectManager.getInstance().openProjects) {
    if (!project.isInitialized || project.isDisposed) {
      continue
    }
    
    for (provider in UPDATE_SETTINGS_PROVIDER_EP.getExtensions(project)) {
      LOG.runAndLogException {
        to.addAll(provider.getPluginRepositories())
      }
    }
  }
}