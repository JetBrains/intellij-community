// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.project.Project
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntityBuilder
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.WsmSingletonEntityUtils.addOrModifySingleEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeJpsEntitySourceFactoryInternal
import com.intellij.workspaceModel.ide.legacyBridge.LegacyBridgeJpsEntitySourceFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object WsmProjectSettingsEntityUtils {
  fun addOrModifyProjectSettingsEntity(
    project: Project,
    mutableStorage: MutableEntityStorage,
    modFunction: (ProjectSettingsEntityBuilder) -> Unit,
  ) {
    val sourceFactory = LegacyBridgeJpsEntitySourceFactory.Companion.getInstance(project) as LegacyBridgeJpsEntitySourceFactoryInternal
    val entitySource: EntitySource = sourceFactory.createEntitySourceForProjectSettings() ?: return // do not touch default project
    addOrModifySingleEntity(mutableStorage,
                            ProjectSettingsEntity::class.java, ProjectSettingsEntityBuilder::class.java,
                            { ProjectSettingsEntity(entitySource) }, modFunction)
  }
}