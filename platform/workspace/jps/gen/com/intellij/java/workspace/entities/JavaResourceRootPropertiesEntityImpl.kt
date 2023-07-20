// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.workspace.entities

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
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
import com.intellij.platform.workspace.storage.impl.extractOneToManyParent
import com.intellij.platform.workspace.storage.impl.updateOneToManyParentOfChild

@GeneratedCodeApiVersion(2)
@GeneratedCodeImplVersion(2)
open class JavaResourceRootPropertiesEntityImpl(val dataSource: JavaResourceRootPropertiesEntityData) : JavaResourceRootPropertiesEntity, WorkspaceEntityBase() {

  companion object {
    internal val SOURCEROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(SourceRootEntity::class.java,
                                                                              JavaResourceRootPropertiesEntity::class.java,
                                                                              ConnectionId.ConnectionType.ONE_TO_MANY, false)

    val connections = listOf<ConnectionId>(
      SOURCEROOT_CONNECTION_ID,
    )

  }

  override val sourceRoot: SourceRootEntity
    get() = snapshot.extractOneToManyParent(SOURCEROOT_CONNECTION_ID, this)!!

  override val generated: Boolean get() = dataSource.generated
  override val relativeOutputPath: String
    get() = dataSource.relativeOutputPath

  override val entitySource: EntitySource
    get() = dataSource.entitySource

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(result: JavaResourceRootPropertiesEntityData?) : ModifiableWorkspaceEntityBase<JavaResourceRootPropertiesEntity, JavaResourceRootPropertiesEntityData>(
    result), JavaResourceRootPropertiesEntity.Builder {
    constructor() : this(JavaResourceRootPropertiesEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity JavaResourceRootPropertiesEntity is already created in a different builder")
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

    fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(SOURCEROOT_CONNECTION_ID, this) == null) {
          error("Field JavaResourceRootPropertiesEntity#sourceRoot should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)] == null) {
          error("Field JavaResourceRootPropertiesEntity#sourceRoot should be initialized")
        }
      }
      if (!getEntityData().isRelativeOutputPathInitialized()) {
        error("Field JavaResourceRootPropertiesEntity#relativeOutputPath should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as JavaResourceRootPropertiesEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.generated != dataSource.generated) this.generated = dataSource.generated
      if (this.relativeOutputPath != dataSource.relativeOutputPath) this.relativeOutputPath = dataSource.relativeOutputPath
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var sourceRoot: SourceRootEntity
      get() {
        val _diff = diff
        return if (_diff != null) {
          _diff.extractOneToManyParent(SOURCEROOT_CONNECTION_ID, this) ?: this.entityLinks[EntityLink(false,
                                                                                                      SOURCEROOT_CONNECTION_ID)]!! as SourceRootEntity
        }
        else {
          this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)]!! as SourceRootEntity
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(SOURCEROOT_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)] = value
        }
        changedProperty.add("sourceRoot")
      }

    override var generated: Boolean
      get() = getEntityData().generated
      set(value) {
        checkModificationAllowed()
        getEntityData(true).generated = value
        changedProperty.add("generated")
      }

    override var relativeOutputPath: String
      get() = getEntityData().relativeOutputPath
      set(value) {
        checkModificationAllowed()
        getEntityData(true).relativeOutputPath = value
        changedProperty.add("relativeOutputPath")
      }

    override fun getEntityClass(): Class<JavaResourceRootPropertiesEntity> = JavaResourceRootPropertiesEntity::class.java
  }
}

class JavaResourceRootPropertiesEntityData : WorkspaceEntityData<JavaResourceRootPropertiesEntity>() {
  var generated: Boolean = false
  lateinit var relativeOutputPath: String


  fun isRelativeOutputPathInitialized(): Boolean = ::relativeOutputPath.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<JavaResourceRootPropertiesEntity> {
    val modifiable = JavaResourceRootPropertiesEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.snapshot = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): JavaResourceRootPropertiesEntity {
    return getCached(snapshot) {
      val entity = JavaResourceRootPropertiesEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return JavaResourceRootPropertiesEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return JavaResourceRootPropertiesEntity(generated, relativeOutputPath, entitySource) {
      parents.filterIsInstance<SourceRootEntity>().singleOrNull()?.let { this.sourceRoot = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(SourceRootEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as JavaResourceRootPropertiesEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.generated != other.generated) return false
    if (this.relativeOutputPath != other.relativeOutputPath) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as JavaResourceRootPropertiesEntityData

    if (this.generated != other.generated) return false
    if (this.relativeOutputPath != other.relativeOutputPath) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + generated.hashCode()
    result = 31 * result + relativeOutputPath.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + generated.hashCode()
    result = 31 * result + relativeOutputPath.hashCode()
    return result
  }

  override fun collectClassUsagesData(collector: UsedClassesCollector) {
    collector.sameForAllEntities = true
  }
}
