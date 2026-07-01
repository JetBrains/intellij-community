// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.providers

// @spec ../../../../../../../../../spec/file-layers.spec.md

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.settings.v2.LayerData
import com.intellij.platform.settings.v2.LayerLocality
import com.intellij.platform.settings.v2.LayerProvider
import com.intellij.platform.settings.v2.SaveResult
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsValue.MapValue
import com.intellij.platform.settings.v2.impl.ProjectSettingsContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

/**
 * Backs one project's layer, stored in `.jetbrains/project.settings.json` and its locality siblings, and applied to the
 * project context. The service watches the files from its creation at the project open, also a file that is still
 * missing. The vault persists the writes through [writeData] after a debounce (ADR 0061), and a file is created on
 * its first write (ADR 0152). A project with no base path, such as the default project, has no files, so its saves
 * are dropped (ADR 0122).
 */
@Service(Service.Level.PROJECT)
internal class ProjectLayerService(private val project: Project, scope: CoroutineScope) : BaseLayerService(scope) {
  private val projectContext = ProjectSettingsContext(project)

  override val providerClass: Class<out LayerProvider> = ProjectLayerProvider::class.java

  init {
    scope.launch {
      for (locality in localities) {
        val file = configFile(locality) ?: continue
        watchConfigFile(this@ProjectLayerService, file) { refresh(projectContext) }
      }
    }
  }

  override suspend fun read(storageContext: SettingsContext, locality: LayerLocality): MapValue {
    val file = configFile(locality) ?: return MapValue(emptyMap())
    return withContext(Dispatchers.IO) { JsonConfigFile.read(file) }
  }

  /** Returns [SaveResult.DROPPED] when the project has no base path, else writes as [BaseLayerService.writeData]. */
  override suspend fun writeData(storageContext: SettingsContext, locality: LayerLocality, slices: Map<SettingsContext, MapValue>): SaveResult {
    if (project.basePath == null) {
      logDroppedSave(storageContext, locality, slices, "the project has no base path")
      return SaveResult.DROPPED
    }
    return super.writeData(storageContext, locality, slices)
  }

  override suspend fun write(storageContext: SettingsContext, locality: LayerLocality, data: MapValue) {
    val file = checkNotNull(configFile(locality)) { "Project '${project.name}' has no base path" }
    withContext(Dispatchers.IO) { JsonConfigFile.write(file, data) }
  }

  // The context is already known to be this project's (the provider resolves the service by it), so nothing else to gate.
  override fun availableLocalities(context: SettingsContext): Set<LayerLocality> = localities.toSet()

  override suspend fun requestData(scopeContext: SettingsContext): List<LayerData> = load(projectContext)

  private fun configFile(locality: LayerLocality): Path? =
    project.basePath?.let { Path.of(it, CONFIG_DIR, localityFileName(FILE_NAME, locality)) }

  companion object {
    /** Holds the name of the directory under the base path of a project that holds the project config files. */
    const val CONFIG_DIR: String = JETBRAINS_CONFIG_NAME

    const val FILE_NAME: String = "project.settings.json"

    fun getInstance(project: Project): ProjectLayerService = project.service()
  }
}
