// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.providers

// @spec ../../../../../../../../../spec/file-layers.spec.md

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.settings.v2.LayerData
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsContextKind
import com.intellij.platform.settings.v2.SettingsContextKindRange
import com.intellij.platform.settings.v2.impl.AppSettingsContext
import com.intellij.platform.settings.v2.impl.ProjectSettingsContext

/** Provides the project layer: a `.jetbrains/project.settings.json` per project. [ProjectLayerService] holds the state of each project. */
internal class ProjectLayerProvider : BaseLayerProvider() {
  override val contextKindRange: SettingsContextKindRange = SettingsContextKindRange(SettingsContextKind.PROJECT, SettingsContextKind.PROJECT)

  override fun service(context: SettingsContext): BaseLayerService? {
    val target = context.realContext as? ProjectSettingsContext ?: return null
    val project = target.project ?: return null
    return ProjectLayerService.getInstance(project)
  }

  override suspend fun requestData(scopeContext: SettingsContext): List<LayerData> {
    val projects: List<Project> = when (val context = scopeContext.realContext) {
      is AppSettingsContext -> ProjectManager.getInstance().openProjects.toList()
      is ProjectSettingsContext -> listOfNotNull(context.project)
      else -> emptyList()
    }
    return collectFromProjects(projects) { ProjectLayerService.getInstance(it).requestData(scopeContext) }
  }
}
