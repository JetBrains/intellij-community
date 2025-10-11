// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.versionCatalogs

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
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

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<GradleVersionCatalogEntity> {
    override var entitySource: EntitySource
    var name: String
    var url: VirtualFileUrl
    var build: GradleBuildEntity.Builder
  }

  companion object : EntityType<GradleVersionCatalogEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      url: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.name = name
      builder.url = url
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyGradleVersionCatalogEntity(
  entity: GradleVersionCatalogEntity,
  modification: GradleVersionCatalogEntity.Builder.() -> Unit,
): GradleVersionCatalogEntity = modifyEntity(GradleVersionCatalogEntity.Builder::class.java, entity, modification)

var GradleBuildEntity.Builder.versionCatalogs: List<GradleVersionCatalogEntity.Builder>
  by WorkspaceEntity.extensionBuilder(GradleVersionCatalogEntity::class.java)
//endregion

val GradleBuildEntity.versionCatalogs: List<@Child GradleVersionCatalogEntity>
  by WorkspaceEntity.extension()

fun GradleBuildEntity.versionCatalog(catalogName: String): GradleVersionCatalogEntity? {
  return versionCatalogs.find { it.name == catalogName }
}