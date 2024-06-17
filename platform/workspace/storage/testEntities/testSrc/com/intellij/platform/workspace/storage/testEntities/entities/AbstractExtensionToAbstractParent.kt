// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Abstract
import com.intellij.platform.workspace.storage.annotations.Child


// THESE ENTITIES ARE INCORRECTLY GENERATED. SEE IDEA-327859

interface ChildWithExtensionParent : WorkspaceEntity {
  val data: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildWithExtensionParent> {
    override var entitySource: EntitySource
    var data: String
  }

  companion object : EntityType<ChildWithExtensionParent, Builder>() {
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
fun MutableEntityStorage.modifyChildWithExtensionParent(
  entity: ChildWithExtensionParent,
  modification: ChildWithExtensionParent.Builder.() -> Unit,
): ChildWithExtensionParent {
  return modifyEntity(ChildWithExtensionParent.Builder::class.java, entity, modification)
}

var ChildWithExtensionParent.Builder.parent: AbstractParentEntity.Builder<out AbstractParentEntity>?
  by WorkspaceEntity.extensionBuilder(AbstractParentEntity::class.java)
//endregion

@Abstract
interface AbstractParentEntity : WorkspaceEntity {
  val data: String
  val child: @Child ChildWithExtensionParent?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder<T : AbstractParentEntity> : WorkspaceEntity.Builder<T> {
    override var entitySource: EntitySource
    var data: String
    var child: ChildWithExtensionParent.Builder?
  }

  companion object : EntityType<AbstractParentEntity, Builder<AbstractParentEntity>>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      data: String,
      entitySource: EntitySource,
      init: (Builder<AbstractParentEntity>.() -> Unit)? = null,
    ): Builder<AbstractParentEntity> {
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
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SpecificParent>, AbstractParentEntity.Builder<SpecificParent> {
    override var entitySource: EntitySource
    override var data: String
    override var child: ChildWithExtensionParent.Builder?
  }

  companion object : EntityType<SpecificParent, Builder>(AbstractParentEntity) {
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
fun MutableEntityStorage.modifySpecificParent(
  entity: SpecificParent,
  modification: SpecificParent.Builder.() -> Unit,
): SpecificParent {
  return modifyEntity(SpecificParent.Builder::class.java, entity, modification)
}
//endregion

val ChildWithExtensionParent.parent: AbstractParentEntity?
  by WorkspaceEntity.extension()