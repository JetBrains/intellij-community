// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

/**
 * Describes a URL excluded from [content root][com.intellij.openapi.roots.ContentEntry.getExcludeFolderUrls] or
 * [library][com.intellij.openapi.roots.impl.libraries.LibraryEx.getExcludedRootUrls].
 * This entity must not be used to specify other excluded roots, define a custom entity instead.
 */
interface ExcludeUrlEntity : WorkspaceEntity {
  val url: VirtualFileUrl

  //region generated code
  @Deprecated(message = "Use ExcludeUrlEntityBuilder instead")
  interface Builder : ExcludeUrlEntityBuilder
  companion object : EntityType<ExcludeUrlEntity, Builder>() {
    @Deprecated(message = "Use new API instead")
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      url: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder = ExcludeUrlEntityType.compatibilityInvoke(url, entitySource, init)
  }
  //endregion
}

//region generated code
@Deprecated(message = "Use new API instead")
fun MutableEntityStorage.modifyExcludeUrlEntity(
  entity: ExcludeUrlEntity,
  modification: ExcludeUrlEntity.Builder.() -> Unit,
): ExcludeUrlEntity {
  return modifyEntity(ExcludeUrlEntity.Builder::class.java, entity, modification)
}

@Deprecated(message = "Use new API instead")
@Parent
var ExcludeUrlEntity.Builder.contentRoot: ContentRootEntity.Builder?
  get() = (this as ExcludeUrlEntityBuilder).contentRoot as ContentRootEntity.Builder?
  set(value) {
    (this as ExcludeUrlEntityBuilder).contentRoot = value
  }

@Deprecated(message = "Use new API instead")
@Parent
var ExcludeUrlEntity.Builder.library: LibraryEntity.Builder?
  get() = (this as ExcludeUrlEntityBuilder).library as LibraryEntity.Builder?
  set(value) {
    (this as ExcludeUrlEntityBuilder).library = value
  }
//endregion
