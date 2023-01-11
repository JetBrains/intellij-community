package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion

import com.intellij.workspaceModel.storage.MutableEntityStorage
import java.util.*


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
  @GeneratedCodeApiVersion(1)
  interface Builder : SampleWithSymbolicIdEntity, WorkspaceEntity.Builder<SampleWithSymbolicIdEntity>, ObjBuilder<SampleWithSymbolicIdEntity> {
    override var entitySource: EntitySource
    override var booleanProperty: Boolean
    override var stringProperty: String
    override var stringListProperty: MutableList<String>
    override var stringMapProperty: Map<String, String>
    override var fileProperty: VirtualFileUrl
    override var children: List<ChildWpidSampleEntity>
    override var nullableData: String?
  }

  companion object : Type<SampleWithSymbolicIdEntity, Builder>() {
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
  @GeneratedCodeApiVersion(1)
  interface Builder : ChildWpidSampleEntity, WorkspaceEntity.Builder<ChildWpidSampleEntity>, ObjBuilder<ChildWpidSampleEntity> {
    override var entitySource: EntitySource
    override var data: String
    override var parentEntity: SampleWithSymbolicIdEntity?
  }

  companion object : Type<ChildWpidSampleEntity, Builder>() {
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
