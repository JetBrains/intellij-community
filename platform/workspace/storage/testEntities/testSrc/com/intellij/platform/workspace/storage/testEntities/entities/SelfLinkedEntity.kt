// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child


interface SelfLinkedEntity : WorkspaceEntity {
  val parentEntity: SelfLinkedEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SelfLinkedEntity> {
    override var entitySource: EntitySource
    var parentEntity: SelfLinkedEntity.Builder?
  }

  companion object : EntityType<SelfLinkedEntity, Builder>() {
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
fun MutableEntityStorage.modifySelfLinkedEntity(
  entity: SelfLinkedEntity,
  modification: SelfLinkedEntity.Builder.() -> Unit,
): SelfLinkedEntity {
  return modifyEntity(SelfLinkedEntity.Builder::class.java, entity, modification)
}

var SelfLinkedEntity.Builder.children: @Child List<SelfLinkedEntity.Builder>
  by WorkspaceEntity.extensionBuilder(SelfLinkedEntity::class.java)
//endregion

val SelfLinkedEntity.children: List<@Child SelfLinkedEntity>
    by WorkspaceEntity.extension()
