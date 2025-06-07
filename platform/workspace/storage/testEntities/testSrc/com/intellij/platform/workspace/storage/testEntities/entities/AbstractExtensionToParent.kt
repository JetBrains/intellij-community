// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child

interface ParentWithLinkToAbstractChild : WorkspaceEntity {
  val data: String
  val child: @Child AbstractChildWithLinkToParentEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ParentWithLinkToAbstractChild> {
    override var entitySource: EntitySource
    var data: String
    var child: AbstractChildWithLinkToParentEntity.Builder<out AbstractChildWithLinkToParentEntity>?
  }

  companion object : EntityType<ParentWithLinkToAbstractChild, Builder>() {
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
fun MutableEntityStorage.modifyParentWithLinkToAbstractChild(
  entity: ParentWithLinkToAbstractChild,
  modification: ParentWithLinkToAbstractChild.Builder.() -> Unit,
): ParentWithLinkToAbstractChild {
  return modifyEntity(ParentWithLinkToAbstractChild.Builder::class.java, entity, modification)
}
//endregion

@Abstract
interface AbstractChildWithLinkToParentEntity : WorkspaceEntity {
  val data: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder<T : AbstractChildWithLinkToParentEntity> : WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    var data: String
  }

  companion object : EntityType<AbstractChildWithLinkToParentEntity, Builder<AbstractChildWithLinkToParentEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder<AbstractChildWithLinkToParentEntity>.() -> Unit)? = null,
    ): Builder<AbstractChildWithLinkToParentEntity> {
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
var AbstractChildWithLinkToParentEntity.Builder<out AbstractChildWithLinkToParentEntity>.parent: ParentWithLinkToAbstractChild.Builder?
  by WorkspaceEntity.extensionBuilder(ParentWithLinkToAbstractChild::class.java)
//endregion

interface SpecificChildWithLinkToParentEntity : AbstractChildWithLinkToParentEntity {
  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SpecificChildWithLinkToParentEntity>,
                      AbstractChildWithLinkToParentEntity.Builder<SpecificChildWithLinkToParentEntity> {
    override var entitySource: EntitySource
    override var data: String
  }

  companion object : EntityType<SpecificChildWithLinkToParentEntity, Builder>(AbstractChildWithLinkToParentEntity) {
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
fun MutableEntityStorage.modifySpecificChildWithLinkToParentEntity(
  entity: SpecificChildWithLinkToParentEntity,
  modification: SpecificChildWithLinkToParentEntity.Builder.() -> Unit,
): SpecificChildWithLinkToParentEntity {
  return modifyEntity(SpecificChildWithLinkToParentEntity.Builder::class.java, entity, modification)
}
//endregion


val AbstractChildWithLinkToParentEntity.parent: ParentWithLinkToAbstractChild? by WorkspaceEntity.extension()