// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.bridgeEntities.api

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.EntityInformation
import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.GeneratedCodeApiVersion
import com.intellij.workspaceModel.storage.GeneratedCodeImplVersion
import com.intellij.workspaceModel.storage.ModifiableWorkspaceEntity
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.ConnectionId
import com.intellij.workspaceModel.storage.impl.EntityLink
import com.intellij.workspaceModel.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.UsedClassesCollector
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.storage.impl.WorkspaceEntityData
import com.intellij.workspaceModel.storage.impl.containers.toMutableWorkspaceList
import com.intellij.workspaceModel.storage.impl.extractOneToManyParent
import com.intellij.workspaceModel.storage.impl.updateOneToManyParentOfChild
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.jetbrains.deft.ObjBuilder
import org.jetbrains.deft.Type
import org.jetbrains.deft.annotations.Child

@GeneratedCodeApiVersion(1)
@GeneratedCodeImplVersion(1)
open class JavaResourceRootEntityImpl(val dataSource: JavaResourceRootEntityData) : JavaResourceRootEntity, WorkspaceEntityBase() {

  companion object {
    internal val SOURCEROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(SourceRootEntity::class.java,
                                                                              JavaResourceRootEntity::class.java,
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

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  class Builder(val result: JavaResourceRootEntityData?) : ModifiableWorkspaceEntityBase<JavaResourceRootEntity>(), JavaResourceRootEntity.Builder {
    constructor() : this(JavaResourceRootEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity JavaResourceRootEntity is already created in a different builder")
        }
      }

      this.diff = builder
      this.snapshot = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()

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
          error("Field JavaResourceRootEntity#sourceRoot should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)] == null) {
          error("Field JavaResourceRootEntity#sourceRoot should be initialized")
        }
      }
      if (!getEntityData().isRelativeOutputPathInitialized()) {
        error("Field JavaResourceRootEntity#relativeOutputPath should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as JavaResourceRootEntity
      this.entitySource = dataSource.entitySource
      this.generated = dataSource.generated
      this.relativeOutputPath = dataSource.relativeOutputPath
      if (parents != null) {
        this.sourceRoot = parents.filterIsInstance<SourceRootEntity>().single()
      }
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData().entitySource = value
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
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
            val data = (value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(SOURCEROOT_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*>) {
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
        getEntityData().generated = value
        changedProperty.add("generated")
      }

    override var relativeOutputPath: String
      get() = getEntityData().relativeOutputPath
      set(value) {
        checkModificationAllowed()
        getEntityData().relativeOutputPath = value
        changedProperty.add("relativeOutputPath")
      }

    override fun getEntityData(): JavaResourceRootEntityData = result ?: super.getEntityData() as JavaResourceRootEntityData
    override fun getEntityClass(): Class<JavaResourceRootEntity> = JavaResourceRootEntity::class.java
  }
}

class JavaResourceRootEntityData : WorkspaceEntityData<JavaResourceRootEntity>() {
  var generated: Boolean = false
  lateinit var relativeOutputPath: String


  fun isRelativeOutputPathInitialized(): Boolean = ::relativeOutputPath.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): ModifiableWorkspaceEntity<JavaResourceRootEntity> {
    val modifiable = JavaResourceRootEntityImpl.Builder(null)
    modifiable.allowModifications {
      modifiable.diff = diff
      modifiable.snapshot = diff
      modifiable.id = createEntityId()
      modifiable.entitySource = this.entitySource
    }
    modifiable.changedProperty.clear()
    return modifiable
  }

  override fun createEntity(snapshot: EntityStorage): JavaResourceRootEntity {
    return getCached(snapshot) {
      val entity = JavaResourceRootEntityImpl(this)
      entity.entitySource = entitySource
      entity.snapshot = snapshot
      entity.id = createEntityId()
      entity
    }
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return JavaResourceRootEntity::class.java
  }

  override fun serialize(ser: EntityInformation.Serializer) {
  }

  override fun deserialize(de: EntityInformation.Deserializer) {
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity>): WorkspaceEntity {
    return JavaResourceRootEntity(generated, relativeOutputPath, entitySource) {
      this.sourceRoot = parents.filterIsInstance<SourceRootEntity>().single()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(SourceRootEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as JavaResourceRootEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.generated != other.generated) return false
    if (this.relativeOutputPath != other.relativeOutputPath) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this::class != other::class) return false

    other as JavaResourceRootEntityData

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
