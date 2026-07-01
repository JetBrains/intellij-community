// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.providers

// @spec ../../../../../../../../../spec/file-layers.spec.md

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.settings.v2.LayerData
import com.intellij.platform.settings.v2.LayerLocality
import com.intellij.platform.settings.v2.LayerProvider
import com.intellij.platform.settings.v2.SaveResult
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsValue.MapValue

/**
 * Implements a stateless [LayerProvider] over a [BaseLayerService]. It holds no state itself, as the EP contract
 * requires, and forwards each call to the service that backs the target context. The service owns the disk IO, and
 * the vault owns the model. A subclass resolves which service backs a context ([service]). It also computes a whole
 * scope ([requestData]), because one provider can span many services, such as one per open project.
 */
abstract class BaseLayerProvider : LayerProvider {
  /** Returns the service that backs [context], or `null` when this provider does not apply to it. */
  protected abstract fun service(context: SettingsContext): BaseLayerService?

  override fun availableLocalities(context: SettingsContext): Set<LayerLocality> =
    service(context)?.availableLocalities(context) ?: emptySet()

  override fun storageContext(context: SettingsContext): SettingsContext =
    service(context)?.storageContext(context) ?: context.realContext

  // No service means the project of the storage context is closed, so there is nothing left to store into.
  override suspend fun writeData(storageContext: SettingsContext, locality: LayerLocality, slices: Map<SettingsContext, MapValue>): SaveResult =
    service(storageContext)?.writeData(storageContext, locality, slices) ?: SaveResult.DROPPED

  /**
   * Requests the layer data of every project in [projects] concurrently and flattens the results. It is for a provider
   * whose data spans many per-project services. See [collectIsolatedInParallel] for the isolation of a failure. A
   * failed project is logged as an error.
   */
  protected suspend fun collectFromProjects(
    projects: List<Project>,
    request: suspend (Project) -> List<LayerData>,
  ): List<LayerData> =
    collectIsolatedInParallel(projects, { project, e -> LOG.error("Failed to load settings for project '${project.name}'", e) }, request)

  private companion object {
    private val LOG = logger<BaseLayerProvider>()
  }
}
