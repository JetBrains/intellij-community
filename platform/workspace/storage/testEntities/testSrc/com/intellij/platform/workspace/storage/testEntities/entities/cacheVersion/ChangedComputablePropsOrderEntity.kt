// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList

// In this test we can deserialize cache
interface ChangedComputablePropsOrderEntity: WorkspaceEntityWithSymbolicId {
  val someKey: Int
  val computableString: String
    get() = "id = $someKey"
  val names: List<String>
  override val symbolicId: com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropsOrderEntityId
    get() = com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.ChangedComputablePropsOrderEntityId(names)
  val value: Int
  val computableInt: Int
    get() = someKey + value

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ChangedComputablePropsOrderEntity, WorkspaceEntity.Builder<ChangedComputablePropsOrderEntity> {
    override var entitySource: EntitySource
    override var someKey: Int
    override var names: MutableList<String>
    override var value: Int
  }

  companion object : EntityType<ChangedComputablePropsOrderEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(someKey: Int,
                        names: List<String>,
                        value: Int,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): ChangedComputablePropsOrderEntity {
      val builder = builder()
      builder.someKey = someKey
      builder.names = names.toMutableWorkspaceList()
      builder.value = value
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion
}

//region generated code
fun MutableEntityStorage.modifyEntity(entity: ChangedComputablePropsOrderEntity,
                                      modification: ChangedComputablePropsOrderEntity.Builder.() -> Unit): ChangedComputablePropsOrderEntity = modifyEntity(
  ChangedComputablePropsOrderEntity.Builder::class.java, entity, modification)
//endregion

data class ChangedComputablePropsOrderEntityId(val names: List<String>): SymbolicEntityId<ChangedComputablePropsOrderEntity> {
  override val presentableName: String
    get() = names.toString()
}