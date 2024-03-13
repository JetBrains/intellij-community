// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.NonNls

/**
 * Describes a [ContentEntry][com.intellij.openapi.roots.ContentEntry].
 * See [package documentation](psi_element://com.intellij.platform.workspace.jps.entities) for more details.
 */
interface ContentRootEntity : WorkspaceEntity {
    val module: ModuleEntity

    @EqualsBy
    val url: VirtualFileUrl
    val excludedPatterns: List<@NlsSafe String>

    val sourceRoots: List<@Child SourceRootEntity>
    val excludedUrls: List<@Child ExcludeUrlEntity>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ContentRootEntity, WorkspaceEntity.Builder<ContentRootEntity> {
    override var entitySource: EntitySource
    override var module: ModuleEntity
    override var url: VirtualFileUrl
    override var excludedPatterns: MutableList<String>
    override var sourceRoots: List<SourceRootEntity>
    override var excludedUrls: List<ExcludeUrlEntity>
  }

  companion object : EntityType<ContentRootEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      url: VirtualFileUrl,
      excludedPatterns: List<String>,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): ContentRootEntity {
      val builder = builder()
      builder.url = url
      builder.excludedPatterns = excludedPatterns.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(
  entity: ContentRootEntity,
  modification: ContentRootEntity.Builder.() -> Unit,
): ContentRootEntity {
  return modifyEntity(ContentRootEntity.Builder::class.java, entity, modification)
}

var ContentRootEntity.Builder.excludeUrlOrder: @Child ExcludeUrlOrderEntity?
  by WorkspaceEntity.extension()
var ContentRootEntity.Builder.sourceRootOrder: @Child SourceRootOrderEntity?
  by WorkspaceEntity.extension()
//endregion

val ExcludeUrlEntity.contentRoot: ContentRootEntity? by WorkspaceEntity.extension()

/**
 * Describes a [SourceFolder][com.intellij.openapi.roots.SourceFolder].
 * See [package documentation](psi_element://com.intellij.platform.workspace.jps.entities) for more details.
 */
interface SourceRootEntity : WorkspaceEntity {
    val contentRoot: ContentRootEntity

    val url: VirtualFileUrl
    val rootType: @NonNls String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : SourceRootEntity, WorkspaceEntity.Builder<SourceRootEntity> {
    override var entitySource: EntitySource
    override var contentRoot: ContentRootEntity
    override var url: VirtualFileUrl
    override var rootType: String
  }

  companion object : EntityType<SourceRootEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      url: VirtualFileUrl,
      rootType: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): SourceRootEntity {
      val builder = builder()
      builder.url = url
      builder.rootType = rootType
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyEntity(
  entity: SourceRootEntity,
  modification: SourceRootEntity.Builder.() -> Unit,
): SourceRootEntity {
  return modifyEntity(SourceRootEntity.Builder::class.java, entity, modification)
}

var SourceRootEntity.Builder.customSourceRootProperties: @Child CustomSourceRootPropertiesEntity?
  by WorkspaceEntity.extension()
//endregion
