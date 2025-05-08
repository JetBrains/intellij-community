// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child


interface KeyParent : WorkspaceEntity {
  @EqualsBy
  val keyField: String
  val notKeyField: String
  val children: List<@Child KeyChild>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<KeyParent> {
    override var entitySource: EntitySource
    var keyField: String
    var notKeyField: String
    var children: List<KeyChild.Builder>
  }

  companion object : EntityType<KeyParent, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      keyField: String,
      notKeyField: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.keyField = keyField
      builder.notKeyField = notKeyField
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifyKeyParent(
  entity: KeyParent,
  modification: KeyParent.Builder.() -> Unit,
): KeyParent {
  return modifyEntity(KeyParent.Builder::class.java, entity, modification)
}
//endregion

interface KeyChild : WorkspaceEntity {
  val data: String

  val parentEntity : KeyParent

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<KeyChild> {
    override var entitySource: EntitySource
    var data: String
    var parentEntity: KeyParent.Builder
  }

  companion object : EntityType<KeyChild, Builder>() {
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
fun MutableEntityStorage.modifyKeyChild(
  entity: KeyChild,
  modification: KeyChild.Builder.() -> Unit,
): KeyChild {
  return modifyEntity(KeyChild.Builder::class.java, entity, modification)
}
//endregion
