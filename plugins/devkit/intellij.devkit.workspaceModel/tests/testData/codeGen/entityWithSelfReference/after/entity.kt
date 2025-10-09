package com.intellij.workspaceModel.test.api

import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Parent

interface EntityWithSelfRef : WorkspaceEntity {
  val name: String
  @Parent
  val parentRef: EntityWithSelfRef?
  val children: List<EntityWithSelfRef>

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<EntityWithSelfRef> {
    override var entitySource: EntitySource
    var name: String
    var parentRef: EntityWithSelfRef.Builder?
    var children: List<EntityWithSelfRef.Builder>
  }

  companion object : EntityType<EntityWithSelfRef, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      name: String,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.name = name
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntityWithSelfRef(
  entity: EntityWithSelfRef,
  modification: EntityWithSelfRef.Builder.() -> Unit,
): EntityWithSelfRef = modifyEntity(EntityWithSelfRef.Builder::class.java, entity, modification)
//endregion
