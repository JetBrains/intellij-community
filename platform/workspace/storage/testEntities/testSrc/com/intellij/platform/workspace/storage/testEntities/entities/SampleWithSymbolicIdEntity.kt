// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl


interface SampleWithSymbolicIdEntity : WorkspaceEntityWithSymbolicId {
  val booleanProperty: Boolean
  val stringProperty: String
  val stringListProperty: List<String>
  val stringMapProperty: Map<String, String>
  val fileProperty: VirtualFileUrl
  val children: List<@Child ChildWpidSampleEntity>
  val nullableData: String?

  override val symbolicId: SampleSymbolicId
    get() = SampleSymbolicId(stringProperty)

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<SampleWithSymbolicIdEntity> {
    override var entitySource: EntitySource
    var booleanProperty: Boolean
    var stringProperty: String
    var stringListProperty: MutableList<String>
    var stringMapProperty: Map<String, String>
    var fileProperty: VirtualFileUrl
    var children: List<ChildWpidSampleEntity.Builder>
    var nullableData: String?
  }

  companion object : EntityType<SampleWithSymbolicIdEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(
      booleanProperty: Boolean,
      stringProperty: String,
      stringListProperty: List<String>,
      stringMapProperty: Map<String, String>,
      fileProperty: VirtualFileUrl,
      entitySource: EntitySource,
      init: (Builder.() -> Unit)? = null,
    ): Builder {
      val builder = builder()
      builder.booleanProperty = booleanProperty
      builder.stringProperty = stringProperty
      builder.stringListProperty = stringListProperty.toMutableWorkspaceList()
      builder.stringMapProperty = stringMapProperty
      builder.fileProperty = fileProperty
      builder.entitySource = entitySource
      init?.invoke(builder)
      return builder
    }
  }
  //endregion

}

//region generated code
fun MutableEntityStorage.modifySampleWithSymbolicIdEntity(
  entity: SampleWithSymbolicIdEntity,
  modification: SampleWithSymbolicIdEntity.Builder.() -> Unit,
): SampleWithSymbolicIdEntity {
  return modifyEntity(SampleWithSymbolicIdEntity.Builder::class.java, entity, modification)
}
//endregion

data class SampleSymbolicId(val stringProperty: String) : SymbolicEntityId<SampleWithSymbolicIdEntity> {
  override val presentableName: String
    get() = stringProperty
}

interface ChildWpidSampleEntity : WorkspaceEntity {
  val data: String
  val parentEntity: SampleWithSymbolicIdEntity?

  //region generated code
  @GeneratedCodeApiVersion(3)
  interface Builder : WorkspaceEntity.Builder<ChildWpidSampleEntity> {
    override var entitySource: EntitySource
    var data: String
    var parentEntity: SampleWithSymbolicIdEntity.Builder?
  }

  companion object : EntityType<ChildWpidSampleEntity, Builder>() {
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
fun MutableEntityStorage.modifyChildWpidSampleEntity(
  entity: ChildWpidSampleEntity,
  modification: ChildWpidSampleEntity.Builder.() -> Unit,
): ChildWpidSampleEntity {
  return modifyEntity(ChildWpidSampleEntity.Builder::class.java, entity, modification)
}
//endregion
