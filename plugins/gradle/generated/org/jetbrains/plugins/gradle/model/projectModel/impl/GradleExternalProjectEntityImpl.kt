// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package org.jetbrains.plugins.gradle.model.projectModel.impl

import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntity
import com.intellij.platform.externalSystem.impl.workspaceModel.ExternalProjectEntityBuilder
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
import org.jetbrains.plugins.gradle.model.projectModel.GradleExternalProjectEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleExternalProjectEntityBuilder

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleExternalProjectEntityImpl(private val dataSource: GradleExternalProjectEntityData) : GradleExternalProjectEntity,
                                                                                                          WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val EXTERNALPROJECT_CONNECTION_ID: ConnectionId = ConnectionId.create(ExternalProjectEntity::class.java,
                                                                                   GradleExternalProjectEntity::class.java,
                                                                                   ConnectionId.ConnectionType.ONE_TO_ONE,
                                                                                   false)
    private val connections = listOf<ConnectionId>(EXTERNALPROJECT_CONNECTION_ID)
  }

  override val externalProject: ExternalProjectEntity
    get() = snapshot.instrumentation.getParent(EXTERNALPROJECT_CONNECTION_ID, this) as? ExternalProjectEntity
            ?: error("Parent externalProject not found for GradleExternalProjectEntity")
  override val gradleVersion: String
    get() {
      readField("gradleVersion")
      return dataSource.gradleVersion
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: GradleExternalProjectEntityData?) :
    ModifiableWorkspaceEntityBase<GradleExternalProjectEntity, GradleExternalProjectEntityData>(result),
    GradleExternalProjectEntityBuilder {
    internal constructor() : this(GradleExternalProjectEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(EXTERNALPROJECT_CONNECTION_ID, this) == null) {
          error("Field GradleExternalProjectEntity#externalProject should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, EXTERNALPROJECT_CONNECTION_ID)] == null) {
          error("Field GradleExternalProjectEntity#externalProject should be initialized")
        }
      }
      if (!getEntityData().isGradleVersionInitialized()) {
        error("Field GradleExternalProjectEntity#gradleVersion should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as GradleExternalProjectEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.gradleVersion != dataSource.gradleVersion) this.gradleVersion = dataSource.gradleVersion
      updateChildToParentReferences(parents)
    }

    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")
      }
    override var externalProject: ExternalProjectEntityBuilder
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(EXTERNALPROJECT_CONNECTION_ID,
                                                                           this) as? ExternalProjectEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, EXTERNALPROJECT_CONNECTION_ID)] as? ExternalProjectEntityBuilder)
          ?: error("externalProject is null for GradleExternalProjectEntity")
        }
        else {
          (this.entityLinks[EntityLink(false, EXTERNALPROJECT_CONNECTION_ID)] as? ExternalProjectEntityBuilder)
          ?: error("externalProject is null for GradleExternalProjectEntity")
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          value.entityLinks[EntityLink(true, EXTERNALPROJECT_CONNECTION_ID)] = this
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.addChild(EXTERNALPROJECT_CONNECTION_ID, value, this)
        }
        else {
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            value.entityLinks[EntityLink(true, EXTERNALPROJECT_CONNECTION_ID)] = this
          }
          this.entityLinks[EntityLink(false, EXTERNALPROJECT_CONNECTION_ID)] = value
        }
        changedProperty.add("externalProject")
      }
    override var gradleVersion: String
      get() = getEntityData().gradleVersion
      set(value) {
        checkModificationAllowed()
        getEntityData(true).gradleVersion = value
        changedProperty.add("gradleVersion")
      }

    override fun getEntityClass(): Class<GradleExternalProjectEntity> = GradleExternalProjectEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleExternalProjectEntityData : WorkspaceEntityData<GradleExternalProjectEntity>() {
  lateinit var gradleVersion: String
  internal fun isGradleVersionInitialized(): Boolean = ::gradleVersion.isInitialized
  override fun newInstance(): GradleExternalProjectEntity = GradleExternalProjectEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<GradleExternalProjectEntity, *> =
    GradleExternalProjectEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("org.jetbrains.plugins.gradle.model.projectModel.GradleExternalProjectEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return GradleExternalProjectEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return GradleExternalProjectEntity(gradleVersion, entitySource) {
      parents.filterIsInstance<ExternalProjectEntityBuilder>().singleOrNull()?.let { this.externalProject = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ExternalProjectEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as GradleExternalProjectEntityData
    if (this.entitySource != other.entitySource) return false
    if (this.gradleVersion != other.gradleVersion) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false
    other as GradleExternalProjectEntityData
    if (this.gradleVersion != other.gradleVersion) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + gradleVersion.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + gradleVersion.hashCode()
    return result
  }
}
