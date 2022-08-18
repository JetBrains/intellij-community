package com.intellij.workspaceModel.storage.entities.test.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import java.util.*


interface SampleWithPersistentIdEntity : WorkspaceEntityWithPersistentId {
  val booleanProperty: Boolean
  val stringProperty: String
  val stringListProperty: List<String>
  val stringMapProperty: Map<String, String>
  val fileProperty: VirtualFileUrl
  val children: List<@Child ChildWpidSampleEntity>
  val nullableData: String?

  override val persistentId: PersistentEntityId<WorkspaceEntityWithPersistentId>
    get() = SamplePersistentId(stringProperty)

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : SampleWithPersistentIdEntity, ModifiableWorkspaceEntity<SampleWithPersistentIdEntity>, ObjBuilder<SampleWithPersistentIdEntity> {
    override var booleanProperty: Boolean
    override var entitySource: EntitySource
    override var stringProperty: String
    override var stringListProperty: MutableList<String>
    override var stringMapProperty: Map<String, String>
    override var fileProperty: VirtualFileUrl
    override var children: List<ChildWpidSampleEntity>
    override var nullableData: String?
  }

  companion object : Type<SampleWithPersistentIdEntity, Builder>() {
    operator fun invoke(booleanProperty: Boolean,
                        stringProperty: String,
                        stringListProperty: List<String>,
                        stringMapProperty: Map<String, String>,
                        fileProperty: VirtualFileUrl,
                        entitySource: EntitySource,
                        init: (Builder.() -> Unit)? = null): SampleWithPersistentIdEntity {
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
fun MutableEntityStorage.modifyEntity(entity: SampleWithPersistentIdEntity,
                                      modification: SampleWithPersistentIdEntity.Builder.() -> Unit) = modifyEntity(
  SampleWithPersistentIdEntity.Builder::class.java, entity, modification)
//endregion

data class SamplePersistentId(val stringProperty: String) : PersistentEntityId<SampleWithPersistentIdEntity> {
  override val presentableName: String
    get() = stringProperty
}

interface ChildWpidSampleEntity : WorkspaceEntity {
  val data: String
  val parentEntity: SampleWithPersistentIdEntity?

  //region generated code
  @GeneratedCodeApiVersion(1)
  interface Builder : ChildWpidSampleEntity, ModifiableWorkspaceEntity<ChildWpidSampleEntity>, ObjBuilder<ChildWpidSampleEntity> {
    override var data: String
    override var entitySource: EntitySource
    override var parentEntity: SampleWithPersistentIdEntity?
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
