// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.legacyBridge.FacetBridge
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.api.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity

interface ModuleSettingsContributor {
  fun addSettings(project: Project) { }
  fun addSettings(project: Project, module: ModuleEntity, entitySource: EntitySource, mutableStorage: MutableEntityStorage) { }
  fun getFacetBridge(moduleEntity: ModuleEntity, entityStorage: EntityStorage): FacetBridge<*>?

  companion object {
    val EP_NAME: ExtensionPointName<ModuleSettingsContributor> = ExtensionPointName.create("com.intellij.workspaceModel.moduleSettingsContributor")
  }
}