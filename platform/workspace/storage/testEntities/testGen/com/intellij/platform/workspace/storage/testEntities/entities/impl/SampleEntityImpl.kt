// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.ChildSampleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.ChildSampleEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntityBuilder
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.util.UUID

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SampleEntityImpl(private val dataSource: SampleEntityData) : SampleEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val CHILDREN_CONNECTION_ID: ConnectionId =
      ConnectionId.create(SampleEntity::class.java, ChildSampleEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true)
    private val connections = listOf<ConnectionId>(CHILDREN_CONNECTION_ID)
  }

  override val booleanProperty: Boolean
    get() {
      readField("booleanProperty")
      return dataSource.booleanProperty
    }
  override val stringProperty: String
    get() {
      readField("stringProperty")
      return dataSource.stringProperty
    }
  override val stringListProperty: List<String>
    get() {
      readField("stringListProperty")
      return dataSource.stringListProperty
    }
  override val stringMapProperty: Map<String, String>
    get() {
      readField("stringMapProperty")
      return dataSource.stringMapProperty
    }
  override val fileProperty: VirtualFileUrl
    get() {
      readField("fileProperty")
      return dataSource.fileProperty
    }
  override val children: List<ChildSampleEntity>
    @Suppress("UNCHECKED_CAST")
    get() = (snapshot.instrumentation.getManyChildren(CHILDREN_CONNECTION_ID, this) as? Sequence<ChildSampleEntity>)?.toList()
            ?: error("Children list children not found for SampleEntity")
  override val nullableData: String?
    get() {
      readField("nullableData")
      return dataSource.nullableData
    }
  override val randomUUID: UUID?
    get() {
      readField("randomUUID")
      return dataSource.randomUUID
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: SampleEntityData?) : ModifiableWorkspaceEntityBase<SampleEntity, SampleEntityData>(result),
                                                      SampleEntityBuilder {
    internal constructor() : this(SampleEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isStringPropertyInitialized()) {
        error("Field SampleEntity#stringProperty should be initialized")
      }
      if (!getEntityData().isStringListPropertyInitialized()) {
        error("Field SampleEntity#stringListProperty should be initialized")
      }
      if (!getEntityData().isStringMapPropertyInitialized()) {
        error("Field SampleEntity#stringMapProperty should be initialized")
      }
      if (!getEntityData().isFilePropertyInitialized()) {
        error("Field SampleEntity#fileProperty should be initialized")
      }
// Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.instrumentation.getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field SampleEntity#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field SampleEntity#children should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_stringListProperty = getEntityData().stringListProperty
      if (collection_stringListProperty is MutableWorkspaceList<*>) {
        collection_stringListProperty.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as SampleEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.booleanProperty != dataSource.booleanProperty) this.booleanProperty = dataSource.booleanProperty
      if (this.stringProperty != dataSource.stringProperty) this.stringProperty = dataSource.stringProperty
      if (this.stringListProperty != dataSource.stringListProperty) this.stringListProperty = dataSource.stringListProperty.toMutableList()
      if (this.stringMapProperty != dataSource.stringMapProperty) this.stringMapProperty = dataSource.stringMapProperty.toMutableMap()
      if (this.fileProperty != dataSource.fileProperty) this.fileProperty = dataSource.fileProperty
      if (this.nullableData != dataSource?.nullableData) this.nullableData = dataSource.nullableData
      if (this.randomUUID != dataSource?.randomUUID) this.randomUUID = dataSource.randomUUID
      updateChildToParentReferences(parents)
    }

    override fun index() {
      index(this, "fileProperty", this.fileProperty)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var booleanProperty: Boolean
      get() = getEntityData().booleanProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).booleanProperty = value
        changedProperty.add("booleanProperty")
      }
    override var stringProperty: String
      get() = getEntityData().stringProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).stringProperty = value
        changedProperty.add("stringProperty")
      }
    private val stringListPropertyUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("stringListProperty")
    }
    override var stringListProperty: MutableList<String>
      get() {
        val collection_stringListProperty = getEntityData().stringListProperty
        if (collection_stringListProperty !is MutableWorkspaceList) return collection_stringListProperty
        if (diff == null || modifiable.get()) {
          collection_stringListProperty.setModificationUpdateAction(stringListPropertyUpdater)
        }
        else {
          collection_stringListProperty.cleanModificationUpdateAction()
        }
        return collection_stringListProperty
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).stringListProperty = value
        stringListPropertyUpdater.invoke(value)
      }
    override var stringMapProperty: Map<String, String>
      get() = getEntityData().stringMapProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).stringMapProperty = value
        changedProperty.add("stringMapProperty")
      }
    override var fileProperty: VirtualFileUrl
      get() = getEntityData().fileProperty
      set(value) {
        checkModificationAllowed()
        getEntityData(true).fileProperty = value
        changedProperty.add("fileProperty")
        val _diff = diff
        if (_diff != null) index(this, "fileProperty", value)
      }

    // List of non-abstract referenced types
    override var children: List<ChildSampleEntityBuilder>
      get() {
// Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @Suppress("UNCHECKED_CAST")
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this)
            .toList() as List<ChildSampleEntityBuilder>) + (this.entityLinks[EntityLink(true,
                                                                                        CHILDREN_CONNECTION_ID)] as? List<ChildSampleEntityBuilder>
                                                            ?: emptyList())
        }
        else {
          @Suppress("UNCHECKED_CAST")
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<ChildSampleEntityBuilder> ?: emptyList()
        }
      }
      set(value) {
// Setter of the list of non-abstract referenced types
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null) {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *> && (item_value as? ModifiableWorkspaceEntityBase<*, *>)?.diff == null) {
// Backref setup before adding to store
              item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
              @Suppress("UNCHECKED_CAST")
              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.instrumentation.replaceChildren(CHILDREN_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
            }
          }
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] = value
        }
        changedProperty.add("children")
      }
    override var nullableData: String?
      get() = getEntityData().nullableData
      set(value) {
        checkModificationAllowed()
        getEntityData(true).nullableData = value
        changedProperty.add("nullableData")
      }
    override var randomUUID: UUID?
      get() = getEntityData().randomUUID
      set(value) {
        checkModificationAllowed()
        getEntityData(true).randomUUID = value
        changedProperty.add("randomUUID")
      }

    override fun getEntityClass(): Class<SampleEntity> = SampleEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class SampleEntityData : WorkspaceEntityData<SampleEntity>() {
  var booleanProperty: Boolean = false
  lateinit var stringProperty: String
  lateinit var stringListProperty: MutableList<String>
  lateinit var stringMapProperty: Map<String, String>
  lateinit var fileProperty: VirtualFileUrl
  var nullableData: String? = null
  var randomUUID: UUID? = null
  internal fun isStringPropertyInitialized(): Boolean = ::stringProperty.isInitialized
  internal fun isStringListPropertyInitialized(): Boolean = ::stringListProperty.isInitialized
  internal fun isStringMapPropertyInitialized(): Boolean = ::stringMapProperty.isInitialized
  internal fun isFilePropertyInitialized(): Boolean = ::fileProperty.isInitialized
  override fun newInstance(): SampleEntity = SampleEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<SampleEntity, *> = SampleEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.SampleEntity") as EntityMetadata
  }

  override fun clone(): SampleEntityData {
    val clonedEntity = super.clone()
    clonedEntity as SampleEntityData
    clonedEntity.stringListProperty = clonedEntity.stringListProperty.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SampleEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return SampleEntity(booleanProperty, stringProperty, stringListProperty, stringMapProperty, fileProperty, entitySource) {
      this.nullableData = this@SampleEntityData.nullableData
      this.randomUUID = this@SampleEntityData.randomUUID
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SampleEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.booleanProperty != other.booleanProperty) return false
    if (this.stringProperty != other.stringProperty) return false
    if (this.stringListProperty != other.stringListProperty) return false
    if (this.stringMapProperty != other.stringMapProperty) return false
    if (this.fileProperty != other.fileProperty) return false
    if (this.nullableData != other.nullableData) return false
    if (this.randomUUID != other.randomUUID) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as SampleEntityData
    if (this.booleanProperty != other.booleanProperty) return false
    if (this.stringProperty != other.stringProperty) return false
    if (this.stringListProperty != other.stringListProperty) return false
    if (this.stringMapProperty != other.stringMapProperty) return false
    if (this.fileProperty != other.fileProperty) return false
    if (this.nullableData != other.nullableData) return false
    if (this.randomUUID != other.randomUUID) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + booleanProperty.hashCode()
    result = 31 * result + stringProperty.hashCode()
    result = 31 * result + stringListProperty.hashCode()
    result = 31 * result + stringMapProperty.hashCode()
    result = 31 * result + fileProperty.hashCode()
    result = 31 * result + nullableData.hashCode()
    result = 31 * result + randomUUID.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + booleanProperty.hashCode()
    result = 31 * result + stringProperty.hashCode()
    result = 31 * result + stringListProperty.hashCode()
    result = 31 * result + stringMapProperty.hashCode()
    result = 31 * result + fileProperty.hashCode()
    result = 31 * result + nullableData.hashCode()
    result = 31 * result + randomUUID.hashCode()
    return result
  }
}
