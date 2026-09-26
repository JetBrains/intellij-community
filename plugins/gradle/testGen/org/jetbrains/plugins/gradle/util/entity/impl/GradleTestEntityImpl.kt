// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package org.jetbrains.plugins.gradle.util.entity.impl

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase
import org.jetbrains.plugins.gradle.util.entity.GradleTestEntity
import org.jetbrains.plugins.gradle.util.entity.GradleTestEntityBuilder
import org.jetbrains.plugins.gradle.util.entity.GradleTestEntityId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleTestEntityImpl(private val dataSource: GradleTestEntityData) : GradleTestEntity, WorkspaceEntityBase(dataSource) {
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
    return emptyList()
  }

  internal class Builder(result: GradleTestEntityData?) : ModifiableWorkspaceEntityBase<GradleTestEntity, GradleTestEntityData>(result),
                                                          GradleTestEntityBuilder {
    internal constructor() : this(GradleTestEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isPhaseInitialized()) {
        error("Field GradleTestEntity#phase should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return emptyList()
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
  override fun newInstance(): GradleTestEntity = GradleTestEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<GradleTestEntity, *> = GradleTestEntityImpl.Builder(null)
  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("org.jetbrains.plugins.gradle.util.entity.GradleTestEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return GradleTestEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return GradleTestEntity(phase, entitySource)
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
