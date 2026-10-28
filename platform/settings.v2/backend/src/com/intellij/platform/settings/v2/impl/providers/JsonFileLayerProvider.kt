// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.providers

// @spec ../../../../../../../../../spec/file-layers.spec.md

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.settings.v2.LayerData
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsContextKind
import com.intellij.platform.settings.v2.SettingsContextKindRange
import com.intellij.platform.settings.v2.impl.ProjectAware

/** A file layer: a `.jetbrains.json` per directory. Per-project state lives in [FileLayerService]. */
internal class JsonFileLayerProvider : BaseLayerProvider() {
  override val contextKindRange: SettingsContextKindRange = SettingsContextKindRange(SettingsContextKind.FILE, narrowestKind = null)

  // A project context resolves too, not just a file/directory one; availableLocalities (not a null service) rejects a write to it.
  override fun service(context: SettingsContext): BaseLayerService? =
    (context.realContext as? ProjectAware)?.project?.let { FileLayerService.getInstance(it) }

  override suspend fun requestData(scopeContext: SettingsContext): List<LayerData> {
    val projects: List<Project> = when (val context = scopeContext.realContext) {
      is ProjectAware -> listOfNotNull(context.project)
      else -> ProjectManager.getInstance().openProjects.toList()
    }
    return collectFromProjects(projects) { FileLayerService.getInstance(it).requestData(scopeContext) }
  }
}
