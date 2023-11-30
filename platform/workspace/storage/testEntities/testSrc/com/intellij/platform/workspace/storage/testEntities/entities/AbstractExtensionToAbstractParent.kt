// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child


// THESE ENTITIES ARE INCORRECTLY GENERATED. SEE IDEA-327859

interface ChildWithExtensionParent : WorkspaceEntity {
  val data: String

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ChildWithExtensionParent, WorkspaceEntity.Builder<ChildWithExtensionParent> {
    override var entitySource: EntitySource
    override var data: String
  }

  companion object : EntityType<ChildWithExtensionParent, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ChildWithExtensionParent {
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
fun MutableEntityStorage.modifyEntity(entity: ChildWithExtensionParent,
                                      modification: ChildWithExtensionParent.Builder.() -> Unit): ChildWithExtensionParent = modifyEntity(
  ChildWithExtensionParent.Builder::class.java, entity, modification)

var ChildWithExtensionParent.Builder.parent: AbstractParentEntity?
  by WorkspaceEntity.extension()
//endregion

@Abstract
interface AbstractParentEntity : WorkspaceEntity {
  val data: String
  val child: @Child ChildWithExtensionParent?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder<T : AbstractParentEntity> : AbstractParentEntity, WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    override var data: String
    override var child: ChildWithExtensionParent?
  }

  companion object : EntityType<AbstractParentEntity, Builder<AbstractParentEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String,
                        entitySource: EntitySource,
                        init: (Builder<AbstractParentEntity>.() -> Unit)? = null): AbstractParentEntity {
      val builder = builder()
      builder.data = data
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

interface SpecificParent : AbstractParentEntity {
  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : SpecificParent, AbstractParentEntity.Builder<SpecificParent>, WorkspaceEntity.Builder<SpecificParent> {
    override var entitySource: EntitySource
    override var data: String
    override var child: ChildWithExtensionParent?
  }

  companion object : EntityType<SpecificParent, Builder>(AbstractParentEntity) {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): SpecificParent {
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
fun MutableEntityStorage.modifyEntity(entity: SpecificParent,
                                      modification: SpecificParent.Builder.() -> Unit): SpecificParent = modifyEntity(
  SpecificParent.Builder::class.java, entity, modification)
//endregion

val ChildWithExtensionParent.parent: AbstractParentEntity?
  by WorkspaceEntity.extension()