// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

interface TreeMultiparentRootEntity : WorkspaceEntityWithPersistentId {
  val data: String

  val children: List<@Child TreeMultiparentLeafEntity>

  override val persistentId: TreeMultiparentPersistentId
    get() = TreeMultiparentPersistentId(data)

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : TreeMultiparentRootEntity, ModifiableWorkspaceEntity<TreeMultiparentRootEntity>, ObjBuilder<TreeMultiparentRootEntity> {
    override var data: String
    override var entitySource: EntitySource
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
  interface Builder : TreeMultiparentLeafEntity, ModifiableWorkspaceEntity<TreeMultiparentLeafEntity>, ObjBuilder<TreeMultiparentLeafEntity> {
    override var data: String
    override var entitySource: EntitySource
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

data class TreeMultiparentPersistentId(val data: String) : PersistentEntityId<TreeMultiparentRootEntity> {
  override val presentableName: String
    get() = data
}
