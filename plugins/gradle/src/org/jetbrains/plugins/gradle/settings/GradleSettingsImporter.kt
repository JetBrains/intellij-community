/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.gradle.settings

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.settings.ConfigurationHandler
import com.intellij.openapi.project.Project

class GradleSettingsImporter : ConfigurationHandler {
  companion object {
    val LOG = Logger.getInstance(GradleSettingsImporter::class.java)
  }

  override fun apply(project: Project, modelsProvider: IdeModifiableModelsProvider, configuration: ConfigurationData) {
    val gradleIdeSettings = configuration.find("gradleSettings") as? Map<*, *> ?: return

    (gradleIdeSettings["sdkHome"] as? String)?.let { sdkHome ->
      val linkedGradleSettings = GradleSettings.getInstance(project).linkedProjectsSettings
      if (linkedGradleSettings.size > 1) {
        LOG.warn("Attempt to set gradle home for project with multiple gradle projects linked, skipping")
      } else {
        linkedGradleSettings.firstOrNull()?.let {
          it.gradleHome = sdkHome
        }
      }
    }
  }
}