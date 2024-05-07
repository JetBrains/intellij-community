// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProjectConfigurationUtil")

package com.intellij.openapi.project.configuration

import com.intellij.configurationStore.StoreUtil.saveSettings
import com.intellij.configurationStore.saveProjectsAndApp
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.Observation

/**
 * Suspends until all configuration activities in a project are finished.
 *
 * In contrast to [Observation.awaitConfiguration], additionally saves files,
 * so that after the end of this method there are no activities scheduled by the IDE.
 */
suspend fun Project.awaitCompleteProjectConfiguration(callback: ((String) -> Unit)?) {
  // we perform several phases of awaiting here,
  // because we need to be prepared for idempotent side effects from saving
  while (true) {
    val wasModified = Observation.awaitConfiguration(this, callback)
    if (wasModified) {
      saveSettings(componentManager = ApplicationManager.getApplication(), forceSavingAllSettings = true)
      saveProjectsAndApp(forceSavingAllSettings = true, onlyProject = this)
      callback?.invoke("Configuration phase is completed. Initiating another phase to cover possible side effects...") // NON-NLS
    }
    else {
      callback?.invoke("All configuration phases are completed.") // NON-NLS
      break
    }
  }
}