// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.net.URL

interface EntityWithManyImports : WorkspaceEntityWithSymbolicId {
  val version: Int
  val name: String
  val files: List<@Child SimpleEntity>
  val pointer: EntityPointer<SimpleEntity>

  override val symbolicId: SimpleId
    get() = SimpleId(name)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<EntityWithManyImports> {
    override var entitySource: EntitySource
    var version: Int
    var name: String
    var files: List<SimpleEntity.Builder>
    var pointer: EntityPointer
  }

  companion object : EntityType<EntityWithManyImports, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      version: Int,
      name: String,
      pointer: EntityPointer,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.version = version
      builder.name = name
      builder.pointer = pointer
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntityWithManyImports(
  entity: EntityWithManyImports,
  modification: EntityWithManyImports.Builder.() -> Unit,
): EntityWithManyImports {
  return modifyEntity(EntityWithManyImports.Builder::class.java, entity, modification)
}
//endregion

data class SimpleId(val name: String) : SymbolicEntityId<EntityWithManyImports> {
  override val presentableName: String
    get() = name
}

interface SimpleEntity : WorkspaceEntity {
  val url: VirtualFileUrl
  val parent: EntityWithManyImports

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SimpleEntity> {
    override var entitySource: EntitySource
    var url: VirtualFileUrl
    var parent: EntityWithManyImports.Builder
  }

  companion object : EntityType<SimpleEntity, Builder>() {
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
fun MutableEntityStorage.modifySimpleEntity(
  entity: SimpleEntity,
  modification: SimpleEntity.Builder.() -> Unit,
): SimpleEntity {
  return modifyEntity(SimpleEntity.Builder::class.java, entity, modification)
}
//endregion

data class UnrelatedToEntities(val name: String, val data: EntityPointer<SimpleEntity>) {
  fun doSomething(src: EntitySource) {
  }
}
