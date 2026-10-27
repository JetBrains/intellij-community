// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.RegistryManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object StructuralChangesViewSettings {
  const val REGISTRY_KEY: String = "vcs.show.structural.changes"

  /** The strength of the dim around the change of a preview, in percent. */
  const val DIM_REGISTRY_KEY: String = "vcs.show.structural.changes.dim"
  private const val OPTION_KEY = "ChangesView.ShowStructuralChanges"

  fun isAvailable(): Boolean = RegistryManager.getInstance().`is`(REGISTRY_KEY)

  fun isEnabled(project: Project): Boolean = isAvailable() && PropertiesComponent.getInstance(project).getBoolean(OPTION_KEY)

  /** The strength of the dim around the change of a preview, as an alpha from 0 to 1. The dim reads it at each paint. */
  fun previewDimAlpha(): Float = RegistryManager.getInstance().intValue(DIM_REGISTRY_KEY, 65).coerceIn(0, 100) / 100f

  fun setEnabled(project: Project, enabled: Boolean) {
    PropertiesComponent.getInstance(project).setValue(OPTION_KEY, enabled)
    project.messageBus.syncPublisher(ChangesViewModifier.TOPIC).updated()
  }
}
