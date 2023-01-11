// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion

import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

interface TreeMultiparentRootEntity : WorkspaceEntityWithSymbolicId {
  val data: String

  val children: List<@Child TreeMultiparentLeafEntity>

  override val symbolicId: TreeMultiparentSymbolicId
    get() = TreeMultiparentSymbolicId(data)

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : TreeMultiparentRootEntity, WorkspaceEntity.Builder<TreeMultiparentRootEntity>, ObjBuilder<TreeMultiparentRootEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var children: List<TreeMultiparentLeafEntity>
  }

  companion object : Type<TreeMultiparentRootEntity, Builder>() {
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): TreeMultiparentRootEntity {
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
fun MutableEntityStorage.modifyEntity(entity: TreeMultiparentRootEntity,
                                      modification: TreeMultiparentRootEntity.Builder.() -> Unit) = modifyEntity(
  TreeMultiparentRootEntity.Builder::class.java, entity, modification)
//endregion

interface TreeMultiparentLeafEntity : WorkspaceEntity {
  val data: String

  val mainParent: TreeMultiparentRootEntity?
  val leafParent: TreeMultiparentLeafEntity?
  val children: List<@Child TreeMultiparentLeafEntity>

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : TreeMultiparentLeafEntity, WorkspaceEntity.Builder<TreeMultiparentLeafEntity>, ObjBuilder<TreeMultiparentLeafEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var mainParent: TreeMultiparentRootEntity?
    override var leafParent: TreeMultiparentLeafEntity?
    override var children: List<TreeMultiparentLeafEntity>
  }

  companion object : Type<TreeMultiparentLeafEntity, Builder>() {
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): TreeMultiparentLeafEntity {
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
fun MutableEntityStorage.modifyEntity(entity: TreeMultiparentLeafEntity,
                                      modification: TreeMultiparentLeafEntity.Builder.() -> Unit) = modifyEntity(
  TreeMultiparentLeafEntity.Builder::class.java, entity, modification)
//endregion

data class TreeMultiparentSymbolicId(val data: String) : SymbolicEntityId<TreeMultiparentRootEntity> {
  override val presentableName: String
    get() = data
}
