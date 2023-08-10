// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion

import com.intellij.platform.workspace.storage.MutableEntityStorage


interface SampleWithSymbolicIdEntity : WorkspaceEntityWithSymbolicId {
  val booleanProperty: Boolean
  val stringProperty: String
  val stringListProperty: List<String>
  val stringMapProperty: Map<String, String>
  val fileProperty: VirtualFileUrl
  val children: List<@Child ChildWpidSampleEntity>
  val nullableData: String?

  override val symbolicId: SymbolicEntityId<WorkspaceEntityWithSymbolicId>
    get() = SampleSymbolicId(stringProperty)

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : SampleWithSymbolicIdEntity, WorkspaceEntity.Builder<SampleWithSymbolicIdEntity> {
    override var entitySource: EntitySource
    override var booleanProperty: Boolean
    override var stringProperty: String
    override var stringListProperty: MutableList<String>
    override var stringMapProperty: Map<String, String>
    override var fileProperty: VirtualFileUrl
    override var children: List<ChildWpidSampleEntity>
    override var nullableData: String?
  }

  companion object : EntityType<SampleWithSymbolicIdEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(booleanProperty: Boolean,
                        stringProperty: String,
                        stringListProperty: List<String>,
                        stringMapProperty: Map<String, String>,
                        fileProperty: VirtualFileUrl,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): SampleWithSymbolicIdEntity {
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
fun MutableEntityStorage.modifyEntity(entity: SampleWithSymbolicIdEntity,
                                      modification: SampleWithSymbolicIdEntity.Builder.() -> Unit) = modifyEntity(
  SampleWithSymbolicIdEntity.Builder::class.java, entity, modification)
//endregion

data class SampleSymbolicId(val stringProperty: String) : SymbolicEntityId<SampleWithSymbolicIdEntity> {
  override val presentableName: String
    get() = stringProperty
}

interface ChildWpidSampleEntity : WorkspaceEntity {
  val data: String
  val parentEntity: SampleWithSymbolicIdEntity?

  //region generated code
  @GeneratedCodeApiVersion(2)
  interface Builder : ChildWpidSampleEntity, WorkspaceEntity.Builder<ChildWpidSampleEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var parentEntity: SampleWithSymbolicIdEntity?
  }

  companion object : EntityType<ChildWpidSampleEntity, Builder>() {
    @JvmOverloads
    @JvmStatic
    @JvmName("create")
    operator fun invoke(data: String, entitySource: EntitySource, init: (Builder.() -> Unit)? = null): ChildWpidSampleEntity {
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
fun MutableEntityStorage.modifyEntity(entity: ChildWpidSampleEntity, modification: ChildWpidSampleEntity.Builder.() -> Unit) = modifyEntity(
  ChildWpidSampleEntity.Builder::class.java, entity, modification)
//endregion
