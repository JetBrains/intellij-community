// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

// In this test we can deserialize cache
interface KeyPropEntity: WorkspaceEntity {
  val someInt: Int
  val text: String
  val url: VirtualFileUrl // Change is here, property is not key

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<KeyPropEntity> {
    override var entitySource: EntitySource
    var someInt: Int
    var text: String
    var url: VirtualFileUrl
  }

  companion object : EntityType<KeyPropEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      someInt: Int,
      text: String,
      url: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.someInt = someInt
      builder.text = text
      builder.url = url
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyKeyPropEntity(
  entity: KeyPropEntity,
  modification: KeyPropEntity.Builder.() -> Unit,
): KeyPropEntity {
  return modifyEntity(KeyPropEntity.Builder::class.java, entity, modification)
}
//endregion
