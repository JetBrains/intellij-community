// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.EntityInformation
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.UsedClassesCollector
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.extractOneToManyChildren
import com.intellij.platform.workspace.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import java.util.UUID
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(2)
open class SampleEntityImpl(val dataSource: SampleEntityData) : SampleEntity, WorkspaceEntityBase() {

  companion object {
    internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(SampleEntity::class.java, ChildSampleEntity::class.java,
                                                                            ConnectionId.ConnectionType.ONE_TO_MANY, true)

    val connections = listOf<ConnectionId>(
      CHILDREN_CONNECTION_ID,
    )

  }

  override val booleanProperty: Boolean get() = dataSource.booleanProperty
  override val stringProperty: String
    get() = dataSource.stringProperty

  override val stringListProperty: List<String>
    get() = dataSource.stringListProperty

  override val stringMapProperty: Map<String, String>
    get() = dataSource.stringMapProperty
  override val fileProperty: VirtualFileUrl
    get() = dataSource.fileProperty

  override val children: List<ChildSampleEntity>
    get() = snapshot.extractOneToManyChildren<ChildSampleEntity>(CHILDREN_CONNECTION_ID, this)!!.toList()

  override val nullableData: String?
    get() = dataSource.nullableData

  override val randomUUID: UUID?
    get() = dataSource.randomUUID

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: SampleEntityData?) : ModifiableWorkspaceEntityBase<SampleEntity, SampleEntityData>(result), SampleEntity.Builder {
    constructor() : this(SampleEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity SampleEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
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

    fun checkInitialization() {
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
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
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
    var _children: List<ChildSampleEntity>? = emptyList()
    override var children: List<ChildSampleEntity>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyChildren<ChildSampleEntity>(CHILDREN_CONNECTION_ID, this)!!.toList() + (this.entityLinks[EntityLink(true,
                                                                                                                                    CHILDREN_CONNECTION_ID)] as? List<ChildSampleEntity>
                                                                                                        ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<ChildSampleEntity> ?: emptyList()
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

              _diff.addEntity(item_value)
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

class SampleEntityData : WorkspaceEntityData<SampleEntity>() {
  var booleanProperty: Boolean = false
  lateinit var stringProperty: String
  lateinit var stringListProperty: MutableList<String>
  lateinit var stringMapProperty: Map<String, String>
  lateinit var fileProperty: VirtualFileUrl
  var nullableData: String? = null
  var randomUUID: UUID? = null


  fun isStringPropertyInitialized(): Boolean = ::stringProperty.isInitialized
  fun isStringListPropertyInitialized(): Boolean = ::stringListProperty.isInitialized
  fun isStringMapPropertyInitialized(): Boolean = ::stringMapProperty.isInitialized
  fun isFilePropertyInitialized(): Boolean = ::fileProperty.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<SampleEntity> {
    val modifiable = SampleEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): SampleEntity {
    return getCached(snapshot) {
      val entity = SampleEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
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

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
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

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    this.stringMapProperty?.let { collector.add(it::class.java) }
    this.stringListProperty?.let { collector.add(it::class.java) }
    this.randomUUID?.let { collector.addDataToInspect(it) }
    this.fileProperty?.let { collector.add(it::class.java) }
    collector.sameForAllEntities = false
  }
}
