// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.versionCatalogs

import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity

interface GradleVersionCatalogEntity : WorkspaceEntity {
  val name: String
  // version catalog file location
  val url: VirtualFileUrl
  @Parent
  val build: GradleBuildEntity
}

val GradleBuildEntity.versionCatalogs: List<@Child GradleVersionCatalogEntity>
  by WorkspaceEntity.extension()

fun GradleBuildEntity.versionCatalog(catalogName: String): GradleVersionCatalogEntity? {
  return versionCatalogs.find { it.name == catalogName }
}