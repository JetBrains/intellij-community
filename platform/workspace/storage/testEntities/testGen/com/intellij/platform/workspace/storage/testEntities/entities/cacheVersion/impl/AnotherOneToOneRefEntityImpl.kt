// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.impl

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
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.instrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToOneRefEntity
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToOneRefEntityBuilder
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToOneRefEntity
import com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.OneToOneRefEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class AnotherOneToOneRefEntityImpl(private val dataSource: AnotherOneToOneRefEntityData) : AnotherOneToOneRefEntity,
                                                                                                    WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val PARENTENTITY_CONNECTION_ID: ConnectionId = ConnectionId.create(OneToOneRefEntity::class.java,
                                                                                AnotherOneToOneRefEntity::class.java,
                                                                                ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                false)
    private val connections = listOf<ConnectionId>(PARENTENTITY_CONNECTION_ID)
  }

  override val someString: String
    get() {
      readField("someString")
      return dataSource.someString
    }
  override val boolean: Boolean
    get() {
      readField("boolean")
      return dataSource.boolean
    }
  override val parentEntity: OneToOneRefEntity
    get() = snapshot.instrumentation.getParent(PARENTENTITY_CONNECTION_ID, this) as? OneToOneRefEntity
            ?: error("Parent parentEntity not found for AnotherOneToOneRefEntity")
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: AnotherOneToOneRefEntityData?) :
    ModifiableWorkspaceEntityBase<AnotherOneToOneRefEntity, AnotherOneToOneRefEntityData>(result), AnotherOneToOneRefEntityBuilder {
    internal constructor() : this(AnotherOneToOneRefEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isSomeStringInitialized()) {
        error("Field AnotherOneToOneRefEntity#someString should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(PARENTENTITY_CONNECTION_ID, this) == null) {
          error("Field AnotherOneToOneRefEntity#parentEntity should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] == null) {
          error("Field AnotherOneToOneRefEntity#parentEntity should be initialized")
        }
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as AnotherOneToOneRefEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.someString != dataSource.someString) this.someString = dataSource.someString
      if (this.boolean != dataSource.boolean) this.boolean = dataSource.boolean
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
    override var boolean: Boolean
      get() = getEntityData().boolean
      set(value) {
        checkModificationAllowed()
        getEntityData(true).boolean = value
        changedProperty.add("boolean")
      }
    override var parentEntity: OneToOneRefEntityBuilder
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(PARENTENTITY_CONNECTION_ID, this) as? OneToOneRefEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] as? OneToOneRefEntityBuilder)
          ?: error("parentEntity is null for AnotherOneToOneRefEntity")
        }
        else {
          (this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] as? OneToOneRefEntityBuilder)
          ?: error("parentEntity is null for AnotherOneToOneRefEntity")
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = this
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.addChild(PARENTENTITY_CONNECTION_ID, value, this)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, PARENTENTITY_CONNECTION_ID)] = this
          }
          this.entityLinks[EntityLink(false, PARENTENTITY_CONNECTION_ID)] = value
        }
        changedProperty.add("parentEntity")
      }

    override fun getEntityClass(): Class<AnotherOneToOneRefEntity> = AnotherOneToOneRefEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class AnotherOneToOneRefEntityData : WorkspaceEntityData<AnotherOneToOneRefEntity>() {
  lateinit var someString: String
  var boolean: Boolean = false
  internal fun isSomeStringInitialized(): Boolean = ::someString.isInitialized
  override fun newInstance(): AnotherOneToOneRefEntity = AnotherOneToOneRefEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<AnotherOneToOneRefEntity, *> = AnotherOneToOneRefEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.platform.workspace.storage.testEntities.entities.cacheVersion.AnotherOneToOneRefEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return AnotherOneToOneRefEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return AnotherOneToOneRefEntity(someString, boolean, entitySource) {
      parents.filterIsInstance<OneToOneRefEntityBuilder>().singleOrNull()?.let { this.parentEntity = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(OneToOneRefEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as AnotherOneToOneRefEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.someString != other.someString) return false
    if (this.boolean != other.boolean) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as AnotherOneToOneRefEntityData
    if (this.someString != other.someString) return false
    if (this.boolean != other.boolean) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + someString.hashCode()
    result = 31 * result + boolean.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + someString.hashCode()
    result = 31 * result + boolean.hashCode()
    return result
  }
}
