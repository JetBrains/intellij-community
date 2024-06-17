// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.currentVersion

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId

interface ChangedComputablePropEntity: WorkspaceEntityWithSymbolicId {
  val text: String
  override val symbolicId: com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropEntityId
    get() = com.intellij.platform.workspace.storage.testEntities.entities.currentVersion.ChangedComputablePropEntityId(listOf(text, "more text", text))

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChangedComputablePropEntity> {
    override var entitySource: EntitySource
    var text: String
  }

  companion object : EntityType<ChangedComputablePropEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      text: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.text = text
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyChangedComputablePropEntity(
  entity: ChangedComputablePropEntity,
  modification: ChangedComputablePropEntity.Builder.() -> Unit,
): ChangedComputablePropEntity {
  return modifyEntity(ChangedComputablePropEntity.Builder::class.java, entity, modification)
}
//endregion

data class ChangedComputablePropEntityId(val texts: List<String>): SymbolicEntityId<ChangedComputablePropEntity> {
  override val presentableName: String
    get() = texts.joinToString(", ")
}