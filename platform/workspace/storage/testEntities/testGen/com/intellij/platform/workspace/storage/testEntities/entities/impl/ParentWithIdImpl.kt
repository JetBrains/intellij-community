// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.testEntities.entities.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToManyChildren
import com.intellij.platform.workspace.storage.impl.extractOneToManyParent
import com.intellij.platform.workspace.storage.impl.updateOneToManyChildrenOfParent
import com.intellij.platform.workspace.storage.impl.updateOneToManyParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.ChildWithId
import com.intellij.platform.workspace.storage.testEntities.entities.ChildWithIdBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.GrandParentWithId
import com.intellij.platform.workspace.storage.testEntities.entities.GrandParentWithIdBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.ParentWithId
import com.intellij.platform.workspace.storage.testEntities.entities.ParentWithIdBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentWithIdImpl(private val dataSource: ParentWithIdData) : ParentWithId, WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val PARENT_CONNECTION_ID: ConnectionId = ConnectionId.create(GrandParentWithId::class.java, ParentWithId::class.java,
                                                                          ConnectionId.ConnectionType.ONE_TO_MANY, false)
    internal val CHILDREN_CONNECTION_ID: ConnectionId = ConnectionId.create(ParentWithId::class.java, ChildWithId::class.java,
                                                                            ConnectionId.ConnectionType.ONE_TO_MANY, false)

    private val connections = listOf<ConnectionId>(
      PARENT_CONNECTION_ID,
      CHILDREN_CONNECTION_ID,
    )

  }

  override val symbolicId: ParentWithId.ParentId = super.symbolicId

  override val myId: String
    get() {
      readField("myId")
      return dataSource.myId
    }

  override val parent: GrandParentWithId
    get() = snapshot.extractOneToManyParent(PARENT_CONNECTION_ID, this)!!

  override val children: List<ChildWithId>
    get() = snapshot.extractOneToManyChildren<ChildWithId>(CHILDREN_CONNECTION_ID, this)!!.toList()

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: ParentWithIdData?) : ModifiableWorkspaceEntityBase<ParentWithId, ParentWithIdData>(
    result), ParentWithIdBuilder {
    internal constructor() : this(ParentWithIdData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity ParentWithId is already created in a different builder")
        }
      }

      this.diff = builder
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
      if (!getEntityData().isMyIdInitialized()) {
        error("Field ParentWithId#myId should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(PARENT_CONNECTION_ID, this) == null) {
          error("Field ParentWithId#parent should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] == null) {
          error("Field ParentWithId#parent should be initialized")
        }
      }
      // Check initialization for list with ref type
      if (_diff != null) {
        if (_diff.extractOneToManyChildren<WorkspaceEntityBase>(CHILDREN_CONNECTION_ID, this) == null) {
          error("Field ParentWithId#children should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] == null) {
          error("Field ParentWithId#children should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as ParentWithId
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.myId != dataSource.myId) this.myId = dataSource.myId
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var myId: String
      get() = getEntityData().myId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).myId = value
        changedProperty.add("myId")
      }

    override var parent: GrandParentWithIdBuilder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PARENT_CONNECTION_ID, this) as? GrandParentWithIdBuilder)
          ?: (this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)]!! as GrandParentWithIdBuilder)
        }
        else {
          this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)]!! as GrandParentWithIdBuilder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, PARENT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(PARENT_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, PARENT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, PARENT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, PARENT_CONNECTION_ID)] = value
        }
        changedProperty.add("parent")
      }

    // List of non-abstract referenced types
    var _children: List<ChildWithId>? = emptyList()
    override var children: List<ChildWithIdBuilder>
      get() {
        // Getter of the list of non-abstract referenced types
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getManyChildrenBuilders(CHILDREN_CONNECTION_ID,
                                                                                  this)!!.toList() as List<ChildWithIdBuilder>) +
          (this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<ChildWithIdBuilder> ?: emptyList())
        }
        else {
          this.entityLinks[EntityLink(true, CHILDREN_CONNECTION_ID)] as? List<ChildWithIdBuilder> ?: emptyList()
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

    override fun getEntityClass(): Class<ParentWithId> = ParentWithId::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class ParentWithIdData : WorkspaceEntityData<ParentWithId>() {
  lateinit var myId: String

  internal fun isMyIdInitialized(): Boolean = ::myId.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<ParentWithId> {
    val modifiable = ParentWithIdImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): ParentWithId {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = ParentWithIdImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn(
      "com.intellij.platform.workspace.storage.testEntities.entities.ParentWithId") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return ParentWithId::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return ParentWithId(myId, entitySource) {
      parents.filterIsInstance<GrandParentWithIdBuilder>().singleOrNull()?.let { this.parent = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(GrandParentWithId::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ParentWithIdData

    if (this.entitySource != other.entitySource) return false
    if (this.myId != other.myId) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as ParentWithIdData

    if (this.myId != other.myId) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + myId.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + myId.hashCode()
    return result
  }
}
