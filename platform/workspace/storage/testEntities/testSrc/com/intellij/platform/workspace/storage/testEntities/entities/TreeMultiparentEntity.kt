// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child

interface TreeMultiparentRootEntity : WorkspaceEntityWithSymbolicId {
  val data: String

  val children: List<@Child TreeMultiparentLeafEntity>

  override val symbolicId: TreeMultiparentSymbolicId
    get() = TreeMultiparentSymbolicId(data)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<TreeMultiparentRootEntity> {
    override var entitySource: EntitySource
    var data: String
    var children: List<TreeMultiparentLeafEntity.Builder>
  }

  companion object : EntityType<TreeMultiparentRootEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyTreeMultiparentRootEntity(
  entity: TreeMultiparentRootEntity,
  modification: TreeMultiparentRootEntity.Builder.() -> Unit,
): TreeMultiparentRootEntity {
  return modifyEntity(TreeMultiparentRootEntity.Builder::class.java, entity, modification)
}
//endregion

interface TreeMultiparentLeafEntity : WorkspaceEntity {
  val data: String

  val mainParent: TreeMultiparentRootEntity?
  val leafParent: TreeMultiparentLeafEntity?
  val children: List<@Child TreeMultiparentLeafEntity>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<TreeMultiparentLeafEntity> {
    override var entitySource: EntitySource
    var data: String
    var mainParent: TreeMultiparentRootEntity.Builder?
    var leafParent: TreeMultiparentLeafEntity.Builder?
    var children: List<TreeMultiparentLeafEntity.Builder>
  }

  companion object : EntityType<TreeMultiparentLeafEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyTreeMultiparentLeafEntity(
  entity: TreeMultiparentLeafEntity,
  modification: TreeMultiparentLeafEntity.Builder.() -> Unit,
): TreeMultiparentLeafEntity {
  return modifyEntity(TreeMultiparentLeafEntity.Builder::class.java, entity, modification)
}
//endregion

data class TreeMultiparentSymbolicId(val data: String) : SymbolicEntityId<TreeMultiparentRootEntity> {
  override val presentableName: String
    get() = data
}