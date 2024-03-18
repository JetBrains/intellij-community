// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

interface ChangedValueTypeEntity: WorkspaceEntity {
  val type: String
  val someKey: String //Change is here, id: Int --> id: String
  val text: List<String>

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ChangedValueTypeEntity, WorkspaceEntity.Builder<ChangedValueTypeEntity> {
    override var entitySource: EntitySource
    override var type: String
    override var someKey: String
    override var text: MutableList<String>
  }

  companion object : EntityType<ChangedValueTypeEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(type: String,
                        someKey: String,
                        text: List<String>,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ChangedValueTypeEntity {
      val builder = builder()
      builder.type = type
      builder.someKey = someKey
      builder.text = text.toMutableWorkspaceList()
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ChangedValueTypeEntity,
                                      modification: ChangedValueTypeEntity.Builder.() -> Unit): ChangedValueTypeEntity = modifyEntity(
  ChangedValueTypeEntity.Builder::class.java, entity, modification)
//endregion
