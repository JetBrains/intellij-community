// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child


interface ParentSubEntity : WorkspaceEntity {
  val parentData: String

  @Child
  val child: ChildSubEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ParentSubEntity> {
    override var entitySource: EntitySource
    var parentData: String
    var child: ChildSubEntity.Builder?
  }

  companion object : EntityType<ParentSubEntity, Builder>() {
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
fun MutableEntityStorage.modifyParentSubEntity(
  entity: ParentSubEntity,
  modification: ParentSubEntity.Builder.() -> Unit,
): ParentSubEntity {
  return modifyEntity(ParentSubEntity.Builder::class.java, entity, modification)
}
//endregion

interface ChildSubEntity : WorkspaceEntity {
  val parentEntity: ParentSubEntity

  @Child
  val child: ChildSubSubEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildSubEntity> {
    override var entitySource: EntitySource
    var parentEntity: ParentSubEntity.Builder
    var child: ChildSubSubEntity.Builder?
  }

  companion object : EntityType<ChildSubEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyChildSubEntity(
  entity: ChildSubEntity,
  modification: ChildSubEntity.Builder.() -> Unit,
): ChildSubEntity {
  return modifyEntity(ChildSubEntity.Builder::class.java, entity, modification)
}
//endregion

interface ChildSubSubEntity : WorkspaceEntity {
  val parentEntity: ChildSubEntity

  val childData: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildSubSubEntity> {
    override var entitySource: EntitySource
    var parentEntity: ChildSubEntity.Builder
    var childData: String
  }

  companion object : EntityType<ChildSubSubEntity, Builder>() {
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
fun MutableEntityStorage.modifyChildSubSubEntity(
  entity: ChildSubSubEntity,
  modification: ChildSubSubEntity.Builder.() -> Unit,
): ChildSubSubEntity {
  return modifyEntity(ChildSubSubEntity.Builder::class.java, entity, modification)
}
//endregion
