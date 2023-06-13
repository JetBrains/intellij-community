// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspaceModel.jps.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.ObjBuilder
import com.intellij.platform.workspace.storage.Type

interface ExcludeUrlEntity : WorkspaceEntity {
  val url: VirtualFileUrl

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ExcludeUrlEntity, WorkspaceEntity.Builder<ExcludeUrlEntity>, ObjBuilder<ExcludeUrlEntity> {
    override var entitySource: EntitySource
    override var url: VirtualFileUrl
  }

  companion object : Type<ExcludeUrlEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(url: VirtualFileUrl, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ExcludeUrlEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ExcludeUrlEntity, modification: ExcludeUrlEntity.Builder.() -> Unit) = modifyEntity(
  ExcludeUrlEntity.Builder::class.java, entity, modification)

var ExcludeUrlEntity.Builder.contentRoot: ContentRootEntity?
  by WorkspaceEntity.extension()
var ExcludeUrlEntity.Builder.library: LibraryEntity?
  by WorkspaceEntity.extension()
//endregion
