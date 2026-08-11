// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(EntityStorageInstrumentationApi::class)

package com.intellij.java.workspace.entities.impl

import com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.jps.entities.SourceRootEntityBuilder
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

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class JavaResourceRootPropertiesEntityImpl(private val dataSource: JavaResourceRootPropertiesEntityData) :
  JavaResourceRootPropertiesEntity, WorkspaceEntityBase(dataSource) {
  private companion object {
    internal val SOURCEROOT_CONNECTION_ID: ConnectionId = ConnectionId.create(SourceRootEntity::class.java,
                                                                              JavaResourceRootPropertiesEntity::class.java,
                                                                              ConnectionId.ConnectionType.ONE_TO_MANY,
                                                                              false)
    private val connections = listOf<ConnectionId>(SOURCEROOT_CONNECTION_ID)
  }

  override val sourceRoot: SourceRootEntity
    get() = snapshot.instrumentation.getParent(SOURCEROOT_CONNECTION_ID, this) as? SourceRootEntity
            ?: error("Parent sourceRoot not found for JavaResourceRootPropertiesEntity")
  override val generated: Boolean
    get() {
      readField("generated")
      return dataSource.generated
    }
  override val relativeOutputPath: String
    get() {
      readField("relativeOutputPath")
      return dataSource.relativeOutputPath
    }
  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }

  internal class Builder(result: JavaResourceRootPropertiesEntityData?) :
    ModifiableWorkspaceEntityBase<JavaResourceRootPropertiesEntity, JavaResourceRootPropertiesEntityData>(result),
    JavaResourceRootPropertiesEntity.Builder {
    internal constructor() : this(JavaResourceRootPropertiesEntityData())

    override fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.instrumentation.getParentBuilder(SOURCEROOT_CONNECTION_ID, this) == null) {
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
    override var sourceRoot: SourceRootEntityBuilder
      get() {
        val _diff = diff
        return if (_diff != null) {
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(SOURCEROOT_CONNECTION_ID, this) as? SourceRootEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)] as? SourceRootEntityBuilder)
          ?: error("sourceRoot is null for JavaResourceRootPropertiesEntity")
        }
        else {
          (this.entityLinks[EntityLink(false, SOURCEROOT_CONNECTION_ID)] as? SourceRootEntityBuilder)
          ?: error("sourceRoot is null for JavaResourceRootPropertiesEntity")
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
// Setting backref of the list
          @Suppress("UNCHECKED_CAST")
          val data = (value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
          value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] = data
          @Suppress("UNCHECKED_CAST")
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.instrumentation.addChild(SOURCEROOT_CONNECTION_ID, value, this)
        }
        else {
// Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val data = (value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, SOURCEROOT_CONNECTION_ID)] = data
          }
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

@OptIn(WorkspaceEntityInternalApi::class)
internal class JavaResourceRootPropertiesEntityData : WorkspaceEntityData<JavaResourceRootPropertiesEntity>() {
  var generated: Boolean = false
  lateinit var relativeOutputPath: String
  internal fun isRelativeOutputPathInitialized(): Boolean = ::relativeOutputPath.isInitialized
  override fun newInstance(): JavaResourceRootPropertiesEntity = JavaResourceRootPropertiesEntityImpl(this)
  override fun newBuilderInstance(): ModifiableWorkspaceEntityBase<JavaResourceRootPropertiesEntity, *> =
    JavaResourceRootPropertiesEntityImpl.Builder(null)

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("com.intellij.java.workspace.entities.JavaResourceRootPropertiesEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return JavaResourceRootPropertiesEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return JavaResourceRootPropertiesEntity(generated, relativeOutputPath, entitySource) {
      parents.filterIsInstance<SourceRootEntityBuilder>().singleOrNull()?.let { this.sourceRoot = it }
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
}
