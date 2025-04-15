// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectAware
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import java.nio.file.Path

/**
 * Allows providing Gradle scripts for auto-reload tracking.
 * For example, some Gradle plugins may define new script files which affect Gradle loading into the IDE.
 */
@ApiStatus.Internal
interface GradleAutoReloadSettingsCollector {

  /**
   * Collects settings files which will be watched.
   * This property can be called from any thread context to reduce UI freezes and CPU usage.
   * Result will be cached, so settings files should be equals between reloads.
   *
   * @see ExternalSystemProjectAware.settingsFiles
   * @see GradleAutoImportAware.getAffectedExternalProjectFiles
   */
  fun collectSettingsFiles(project: Project, projectSettings: GradleProjectSettings): List<Path>

  companion object {

    @JvmField
    val EP_NAME: ExtensionPointName<GradleAutoReloadSettingsCollector> = ExtensionPointName.create("org.jetbrains.plugins.gradle.autoReloadSettingsCollector")
  }
}
