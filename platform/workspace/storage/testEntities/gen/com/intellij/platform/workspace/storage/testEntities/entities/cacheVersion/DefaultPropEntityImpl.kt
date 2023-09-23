// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion

import com.intellij.platform.workspace.storage.EntityInformation
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(3)
open class DefaultPropEntityImpl(private val dataSource: DefaultPropEntityData) : DefaultPropEntity, WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val someString: String
    get() = dataSource.someString

  override val someList: List<Int>
    get() = dataSource.someList

  override var constInt: Int = dataSource.constInt

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  class Builder(result: DefaultPropEntityData?) : ModifiableWorkspaceEntityBase<DefaultPropEntity, DefaultPropEntityData>(
    result), DefaultPropEntity.Builder {
    constructor() : this(DefaultPropEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity DefaultPropEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isSomeStringInitialized()) {
        error("Field DefaultPropEntity#someString should be initialized")
      }
      if (!getEntityData().isSomeListInitialized()) {
        error("Field DefaultPropEntity#someList should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_someList = getEntityData().someList
      if (collection_someList is MutableWorkspaceList<*>) {
        collection_someList.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as DefaultPropEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.someString != dataSource.someString) this.someString = dataSource.someString
      if (this.someList != dataSource.someList) this.someList = dataSource.someList.toMutableList()
      if (this.constInt != dataSource.constInt) this.constInt = dataSource.constInt
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var someString: String
      get() = getEntityData().someString
      set(value) {
        checkModificationAllowed()
        getEntityData(true).someString = value
        changedProperty.add("someString")
      }

    private val someListUpdater: (value: List<Int>) -> Unit = { value ->

      changedProperty.add("someList")
    }
    override var someList: MutableList<Int>
      get() {
        val collection_someList = getEntityData().someList
        if (collection_someList !is MutableWorkspaceList) return collection_someList
        if (diff == null || modifiable.get()) {
          collection_someList.setModificationUpdateAction(someListUpdater)
        }
        else {
          collection_someList.cleanModificationUpdateAction()
        }
        return collection_someList
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).someList = value
        someListUpdater.invoke(value)
      }

    override var constInt: Int
      get() = getEntityData().constInt
      set(value) {
        checkModificationAllowed()
        getEntityData(true).constInt = value
        changedProperty.add("constInt")
      }

    override fun getEntityClass(): Class<DefaultPropEntity> = DefaultPropEntity::class.java
  }
}

class DefaultPropEntityData : WorkspaceEntityData<DefaultPropEntity>() {
  lateinit var someString: String
  lateinit var someList: MutableList<Int>
  var constInt: Int = 9

  internal fun isSomeStringInitialized(): Boolean = ::someString.isInitialized
  internal fun isSomeListInitialized(): Boolean = ::someList.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<DefaultPropEntity> {
    val modifiable = DefaultPropEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): DefaultPropEntity {
    return getCached(snapshot) {
      val entity = DefaultPropEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.DefaultPropEntity") as EntityMetadata
  }

  override fun clone(): DefaultPropEntityData {
    val clonedEntity = super.clone()
    clonedEntity as DefaultPropEntityData
    clonedEntity.someList = clonedEntity.someList.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return DefaultPropEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return DefaultPropEntity(someString, someList, entitySource) {
      this.constInt = this@DefaultPropEntityData.constInt
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as DefaultPropEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.someString != other.someString) return false
    if (this.someList != other.someList) return false
    if (this.constInt != other.constInt) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as DefaultPropEntityData

    if (this.someString != other.someString) return false
    if (this.someList != other.someList) return false
    if (this.constInt != other.constInt) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + someString.hashCode()
    result = 31 * result + someList.hashCode()
    result = 31 * result + constInt.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + someString.hashCode()
    result = 31 * result + someList.hashCode()
    result = 31 * result + constInt.hashCode()
    return result
  }
}
