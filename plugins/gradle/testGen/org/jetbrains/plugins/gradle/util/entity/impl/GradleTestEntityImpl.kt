// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util.entity.impl

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.util.entity.GradleTestEntity
import org.jetbrains.plugins.gradle.util.entity.GradleTestEntityId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleTestEntityImpl(private val dataSource: GradleTestEntityData) : GradleTestEntity, WorkspaceEntityBase(dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val symbolicId: GradleTestEntityId = super.symbolicId

  override val phase: GradleSyncPhase
    get() {
      readField("phase")
      return dataSource.phase
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: GradleTestEntityData?) : ModifiableWorkspaceEntityBase<GradleTestEntity, GradleTestEntityData>(
    result), GradleTestEntity.Builder {
    internal constructor() : this(GradleTestEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity GradleTestEntity is already created in a different builder")
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
      if (!getEntityData().isPhaseInitialized()) {
        error("Field GradleTestEntity#phase should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as GradleTestEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.phase != dataSource.phase) this.phase = dataSource.phase
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var phase: GradleSyncPhase
      get() = getEntityData().phase
      set(value) {
        checkModificationAllowed()
        getEntityData(true).phase = value
        changedProperty.add("phase")

      }

    override fun getEntityClass(): Class<GradleTestEntity> = GradleTestEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleTestEntityData : WorkspaceEntityData<GradleTestEntity>() {
  lateinit var phase: GradleSyncPhase

  internal fun isPhaseInitialized(): Boolean = ::phase.isInitialized

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<GradleTestEntity> {
    val modifiable = GradleTestEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): GradleTestEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = GradleTestEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("org.jetbrains.plugins.gradle.util.entity.GradleTestEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return GradleTestEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return GradleTestEntity(phase, entitySource) {
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as GradleTestEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.phase != other.phase) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as GradleTestEntityData

    if (this.phase != other.phase) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + phase.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + phase.hashCode()
    return result
  }
}
