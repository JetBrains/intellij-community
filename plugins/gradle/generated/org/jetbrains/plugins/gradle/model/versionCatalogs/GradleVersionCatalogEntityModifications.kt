// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("GradleVersionCatalogEntityModifications")

package org.jetbrains.plugins.gradle.model.versionCatalogs

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityBuilder

@GeneratedCodeApiVersion(3)
interface GradleVersionCatalogEntityBuilder : WorkspaceEntityBuilder<GradleVersionCatalogEntity> {
  override var entitySource: EntitySource
  var name: String
  var url: VirtualFileUrl
  var build: GradleBuildEntityBuilder
}

internal object GradleVersionCatalogEntityType : EntityType<GradleVersionCatalogEntity, GradleVersionCatalogEntityBuilder>() {
  override val entityClass: Class<GradleVersionCatalogEntity> get() = GradleVersionCatalogEntity::class.java
  operator fun invoke(
    name: String,
    url: VirtualFileUrl,
    entitySource: EntitySource,
    init: (GradleVersionCatalogEntityBuilder.() -> Unit)? = null,
  ): GradleVersionCatalogEntityBuilder {
    val builder = builder()
    builder.name = name
    builder.url = url
    builder.entitySource = entitySource
    init?.invoke(builder)
    return builder
  }
}

fun MutableEntityStorage.modifyGradleVersionCatalogEntity(
  entity: GradleVersionCatalogEntity,
  modification: GradleVersionCatalogEntityBuilder.() -> Unit,
): GradleVersionCatalogEntity = modifyEntity(GradleVersionCatalogEntityBuilder::class.java, entity, modification)

var GradleBuildEntityBuilder.versionCatalogs: List<GradleVersionCatalogEntityBuilder>
  by WorkspaceEntity.extensionBuilder(GradleVersionCatalogEntity::class.java)


@JvmOverloads
@JvmName("createGradleVersionCatalogEntity")
fun GradleVersionCatalogEntity(
  name: String,
  url: VirtualFileUrl,
  entitySource: EntitySource,
  init: (GradleVersionCatalogEntityBuilder.() -> Unit)? = null,
): GradleVersionCatalogEntityBuilder = GradleVersionCatalogEntityType(name, url, entitySource, init)
