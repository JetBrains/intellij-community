// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion

import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Child


interface KeyParent : WorkspaceEntity {
  @EqualsBy
  val keyField: String
  val notKeyField: String
  val children: List<@Child KeyChild>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : KeyParent, WorkspaceEntity.Builder<KeyParent> {
    override var entitySource: EntitySource
    override var keyField: String
    override var notKeyField: String
    override var children: List<KeyChild>
  }

  companion object : EntityType<KeyParent, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(keyField: String, notKeyField: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): KeyParent {
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
fun MutableEntityStorage.modifyEntity(entity: KeyParent, modification: KeyParent.Builder.() -> Unit) = modifyEntity(
  KeyParent.Builder::class.java, entity, modification)
//endregion

interface KeyChild : WorkspaceEntity {
  val data: String

  val parentEntity : KeyParent

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : KeyChild, WorkspaceEntity.Builder<KeyChild> {
    override var entitySource: EntitySource
    override var data: String
    override var parentEntity: KeyParent
  }

  companion object : EntityType<KeyChild, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): KeyChild {
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
fun MutableEntityStorage.modifyEntity(entity: KeyChild, modification: KeyChild.Builder.() -> Unit) = modifyEntity(
  KeyChild.Builder::class.java, entity, modification)
//endregion
