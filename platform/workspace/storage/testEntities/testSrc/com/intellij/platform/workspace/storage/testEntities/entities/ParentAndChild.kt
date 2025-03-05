// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child


interface ParentEntity : WorkspaceEntity {
  val parentData: String

  @Child
  val child: ChildEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ParentEntity> {
    override var entitySource: EntitySource
    var parentData: String
    var child: ChildEntity.Builder?
  }

  companion object : EntityType<ParentEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      parentData: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.parentData = parentData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyParentEntity(
  entity: ParentEntity,
  modification: ParentEntity.Builder.() -> Unit,
): ParentEntity {
  return modifyEntity(ParentEntity.Builder::class.java, entity, modification)
}
//endregion

interface ChildEntity : WorkspaceEntity {
  val childData: String

  //    override val parent: ParentEntity
  val parentEntity: ParentEntity

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildEntity> {
    override var entitySource: EntitySource
    var childData: String
    var parentEntity: ParentEntity.Builder
  }

  companion object : EntityType<ChildEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      childData: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.childData = childData
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyChildEntity(
  entity: ChildEntity,
  modification: ChildEntity.Builder.() -> Unit,
): ChildEntity {
  return modifyEntity(ChildEntity.Builder::class.java, entity, modification)
}
//endregion
