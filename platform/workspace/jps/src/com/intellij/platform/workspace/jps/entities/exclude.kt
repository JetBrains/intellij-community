// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

/**
 * Describes a URL excluded from [content root][com.intellij.openapi.roots.ContentEntry.getExcludeFolderUrls] or
 * [library][com.intellij.openapi.roots.impl.libraries.LibraryEx.getExcludedRootUrls].
 * This entity must not be used to specify other excluded roots, define a custom entity instead.
 */
interface ExcludeUrlEntity : WorkspaceEntity {
  val url: VirtualFileUrl

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ExcludeUrlEntity> {
    override var entitySource: EntitySource
    var url: VirtualFileUrl
  }

  companion object : EntityType<ExcludeUrlEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      url: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.url = url
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyExcludeUrlEntity(
  entity: ExcludeUrlEntity,
  modification: ExcludeUrlEntity.Builder.() -> Unit,
): ExcludeUrlEntity {
  return modifyEntity(ExcludeUrlEntity.Builder::class.java, entity, modification)
}

var ExcludeUrlEntity.Builder.contentRoot: ContentRootEntity.Builder?
  by WorkspaceEntity.extensionBuilder(ContentRootEntity::class.java)
var ExcludeUrlEntity.Builder.library: LibraryEntity.Builder?
  by WorkspaceEntity.extensionBuilder(LibraryEntity::class.java)
//endregion
