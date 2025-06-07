// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child


interface MainEntityToParent : WorkspaceEntity {
  val x: String
  val child: @Child AttachedEntityToParent?
  val childNullableParent: @Child AttachedEntityToNullableParent?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<MainEntityToParent> {
    override var entitySource: EntitySource
    var x: String
    var child: AttachedEntityToParent.Builder?
    var childNullableParent: AttachedEntityToNullableParent.Builder?
  }

  companion object : EntityType<MainEntityToParent, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      x: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.x = x
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyMainEntityToParent(
  entity: MainEntityToParent,
  modification: MainEntityToParent.Builder.() -> Unit,
): MainEntityToParent {
  return modifyEntity(MainEntityToParent.Builder::class.java, entity, modification)
}
//endregion

interface AttachedEntityToParent : WorkspaceEntity {
  val data: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<AttachedEntityToParent> {
    override var entitySource: EntitySource
    var data: String
  }

  companion object : EntityType<AttachedEntityToParent, Builder>() {
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
fun MutableEntityStorage.modifyAttachedEntityToParent(
  entity: AttachedEntityToParent,
  modification: AttachedEntityToParent.Builder.() -> Unit,
): AttachedEntityToParent {
  return modifyEntity(AttachedEntityToParent.Builder::class.java, entity, modification)
}

var AttachedEntityToParent.Builder.ref: MainEntityToParent.Builder
  by WorkspaceEntity.extensionBuilder(MainEntityToParent::class.java)
//endregion

val AttachedEntityToParent.ref: MainEntityToParent
    by WorkspaceEntity.extension()


interface AttachedEntityToNullableParent: WorkspaceEntity {
  val data: String

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<AttachedEntityToNullableParent> {
    override var entitySource: EntitySource
    var data: String
  }

  companion object : EntityType<AttachedEntityToNullableParent, Builder>() {
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
fun MutableEntityStorage.modifyAttachedEntityToNullableParent(
  entity: AttachedEntityToNullableParent,
  modification: AttachedEntityToNullableParent.Builder.() -> Unit,
): AttachedEntityToNullableParent {
  return modifyEntity(AttachedEntityToNullableParent.Builder::class.java, entity, modification)
}

var AttachedEntityToNullableParent.Builder.nullableRef: MainEntityToParent.Builder?
  by WorkspaceEntity.extensionBuilder(MainEntityToParent::class.java)
//endregion

val AttachedEntityToNullableParent.nullableRef: MainEntityToParent?
  by WorkspaceEntity.extension()