// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources

import com.intellij.compose.ide.plugin.gradleTooling.rt.ComposeResourcesModel
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService

val COMPOSE_RESOURCES_KEY: Key<ComposeResourcesModel> = Key.create(ComposeResourcesModel::class.java, ProjectKeys.MODULE.processingWeight + 1)

class ComposeResourcesDataService : AbstractProjectDataService<ComposeResourcesModel, Void>() {
  override fun getTargetDataKey(): Key<ComposeResourcesModel> = COMPOSE_RESOURCES_KEY
}