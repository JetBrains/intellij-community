// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type

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
