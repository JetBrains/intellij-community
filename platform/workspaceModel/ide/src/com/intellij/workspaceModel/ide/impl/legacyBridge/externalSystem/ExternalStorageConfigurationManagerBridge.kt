// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.externalSystem

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.project.ExternalStorageConfiguration
import com.intellij.openapi.project.ExternalStorageConfigurationManager
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.JpsImportedEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel

@State(name = "ExternalStorageConfigurationManager")
internal class ExternalStorageConfigurationManagerBridge(private val project: Project)
  : SimplePersistentStateComponent<ExternalStorageConfiguration>(
  ExternalStorageConfiguration()), ExternalStorageConfigurationManager {
  override fun isEnabled(): Boolean = state.enabled

  override fun setEnabled(value: Boolean) {
    state.enabled = value
    ApplicationManager.getApplication().invokeAndWait(Runnable {
      runWriteAction {
        WorkspaceModel.getInstance(project).updateProjectModel { updater ->
          val entitiesMap = updater.entitiesBySource { it is JpsImportedEntitySource && it.storedExternally != value }
          entitiesMap.values.asSequence().flatMap { it.values.asSequence().flatMap { entities -> entities.asSequence() } }.forEach { entity ->
            val source = entity.entitySource
            if (source is JpsImportedEntitySource) {
              updater.changeSource(entity, JpsImportedEntitySource(source.internalFile, source.externalSystemId, value))
            }
          }
        }
      }
    })
  }
}