// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.extractOneToManyChildren
import com.intellij.platform.workspace.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.ChildWpidSampleEntity
import com.intellij.platform.workspace.storage.testEntities.entities.SampleSymbolicId
import com.intellij.platform.workspace.storage.testEntities.entities.SampleWithSymbolicIdEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class SampleWithSymbolicIdEntityImpl(private val dataSource: SampleWithSymbolicIdEntityData) : SampleWithSymbolicIdEntity,
                                                                                                        WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(
      SampleWithSymbolicIdEntity::class.java, ChildWpidSampleEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, true
    )

    private val connections = listOf<ConnectionId>(
      CHILDREN_CONNECTION_ID,
    )

  }

  override val symbolicId: SampleSymbolicId = super.symbolicId

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

  override val children: List<ChildWpidSampleEntity>
    get() = snapshot.extractOneToManyChildren<ChildWpidSampleEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()

  override val nullableData: String?
    get() {
      readField("nullableData")
      return dataSource.nullableData
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: SampleWithSymbolicIdEntityData?) :
    ModifiableWorkspaceEntityBase<SampleWithSymbolicIdEntity, SampleWithSymbolicIdEntityData>(result), SampleWithSymbolicIdEntity.Builder {
    internal constructor() : this(SampleWithSymbolicIdEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity SampleWithSymbolicIdEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "fileProperty", this.fileProperty)
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isStringPropertyInitialized()) {
        error("Field SampleWithSymbolicIdEntity#stringProperty should be initialized")
      }
      if (!getEntityData().isStringListPropertyInitialized()) {
        error("Field SampleWithSymbolicIdEntity#stringListProperty should be initialized")
      }
      if (!getEntityData().isStringMapPropertyInitialized()) {
        error("Field SampleWithSymbolicIdEntity#stringMapProperty should be initialized")
      }
      if (!getEntityData().isFilePropertyInitialized()) {
        error("Field SampleWithSymbolicIdEntity#fileProperty should be initialized")
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field SampleWithSymbolicIdEntity#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field SampleWithSymbolicIdEntity#children should be initialized")
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
      dataSource as SampleWithSymbolicIdEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.booleanProperty != dataSource.booleanProperty) this.booleanProperty = dataSource.booleanProperty
      if (this.stringProperty != dataSource.stringProperty) this.stringProperty = dataSource.stringProperty
      if (this.stringListProperty != dataSource.stringListProperty) this.stringListProperty = dataSource.stringListProperty.toMutableList()
      if (this.stringMapProperty != dataSource.stringMapProperty) this.stringMapProperty = dataSource.stringMapProperty.toMutableMap()
      if (this.fileProperty != dataSource.fileProperty) this.fileProperty = dataSource.fileProperty
      if (this.nullableData != dataSource?.nullableData) this.nullableData = dataSource.nullableData
      updateChildToParentReferences(parents)
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
    var _children: List<ChildWpidSampleEntity>? = emptyList()
    override var children: List<ChildWpidSampleEntity.Builder>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CHILDREN_CONNECTION_ID, this)!!
            .toList() as List<ChildWpidSampleEntity.Builder>) +
          (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<ChildWpidSampleEntity.Builder> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<ChildWpidSampleEntity.Builder> ?: emptyList()
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
              if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
                item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
              }
              // else you're attaching a new entity to an existing entity that is not modifiable

              _diff.addEntity(item_value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
            }
          }
          _diff.updateOneToManyChildrenOfParent(CHILDREN_CONNECTION_ID, this, value)
        }
        else {
          for (item_value in value) {
            if (item_value is ModifiableWorkspaceEntityBase<*, *>) {
              item_value.entityLinks[EntityLink(false, CHILDREN_CONNECTION_ID)] = this
            }
            // else you're attaching a new entity to an existing entity that is not modifiable
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

    override fun getEntityClass(): Class<SampleWithSymbolicIdEntity> = SampleWithSymbolicIdEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class SampleWithSymbolicIdEntityData : WorkspaceEntityData<SampleWithSymbolicIdEntity>() {
  var booleanProperty: Boolean = false
  lateinit var stringProperty: String
  lateinit var stringListProperty: MutableList<String>
  lateinit var stringMapProperty: Map<String, String>
  lateinit var fileProperty: VirtualFileUrl
  var nullableData: String? = null


  internal fun isStringPropertyInitialized(): Boolean = ::stringProperty.isInitialized
  internal fun isStringListPropertyInitialized(): Boolean = ::stringListProperty.isInitialized
  internal fun isStringMapPropertyInitialized(): Boolean = ::stringMapProperty.isInitialized
  internal fun isFilePropertyInitialized(): Boolean = ::fileProperty.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<SampleWithSymbolicIdEntity> {
    val modifiable = SampleWithSymbolicIdEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): SampleWithSymbolicIdEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = SampleWithSymbolicIdEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.SampleWithSymbolicIdEntity"
    ) as EntityMetadata
  }

  override fun clone(): SampleWithSymbolicIdEntityData {
    val clonedEntity = super.clone()
    clonedEntity as SampleWithSymbolicIdEntityData
    clonedEntity.stringListProperty = clonedEntity.stringListProperty.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return SampleWithSymbolicIdEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return SampleWithSymbolicIdEntity(booleanProperty, stringProperty, stringListProperty, stringMapProperty, fileProperty, entitySource) {
      this.nullableData = this@SampleWithSymbolicIdEntityData.nullableData
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SampleWithSymbolicIdEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.booleanProperty != other.booleanProperty) return false
    if (this.stringProperty != other.stringProperty) return false
    if (this.stringListProperty != other.stringListProperty) return false
    if (this.stringMapProperty != other.stringMapProperty) return false
    if (this.fileProperty != other.fileProperty) return false
    if (this.nullableData != other.nullableData) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as SampleWithSymbolicIdEntityData

    if (this.booleanProperty != other.booleanProperty) return false
    if (this.stringProperty != other.stringProperty) return false
    if (this.stringListProperty != other.stringListProperty) return false
    if (this.stringMapProperty != other.stringMapProperty) return false
    if (this.fileProperty != other.fileProperty) return false
    if (this.nullableData != other.nullableData) return false
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
    return result
  }
}
