// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.projectModel.impl

import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Parent
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.extractOneToManyParent
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.impl.updateOneToManyParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityBuilder
import org.jetbrains.plugins.gradle.model.projectModel.GradleBuildEntityId
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntity
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntityBuilder
import org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntityId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleProjectEntityImpl(private val dataSource: GradleProjectEntityData) : GradleProjectEntity, WorkspaceEntityBase(
  dataSource) {

  private companion object {
    internal val BUILD_CONNECTION_ID: ConnectionId = ConnectionId.create(GradleBuildEntity::class.java, GradleProjectEntity::class.java,
                                                                         ConnectionId.ConnectionType.ONE_TO_MANY, false)

    private val connections = listOf<ConnectionId>(
      BUILD_CONNECTION_ID,
    )

  }

  override val symbolicId: GradleProjectEntityId = super.symbolicId

  override val build: GradleBuildEntity
    get() = snapshot.extractOneToManyParent(BUILD_CONNECTION_ID, this)!!

  override val buildId: GradleBuildEntityId
    get() {
      readField("buildId")
      return dataSource.buildId
    }

  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }

  override val path: String
    get() {
      readField("path")
      return dataSource.path
    }

  override val identityPath: String
    get() {
      readField("identityPath")
      return dataSource.identityPath
    }

  override val url: VirtualFileUrl
    get() {
      readField("url")
      return dataSource.url
    }

  override val linkedProjectId: String
    get() {
      readField("linkedProjectId")
      return dataSource.linkedProjectId
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: GradleProjectEntityData?) : ModifiableWorkspaceEntityBase<GradleProjectEntity, GradleProjectEntityData>(
    result), GradleProjectEntityBuilder {
    internal constructor() : this(GradleProjectEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity GradleProjectEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "url", this.url)
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(BUILD_CONNECTION_ID, this) == null) {
          error("Field GradleProjectEntity#build should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, BUILD_CONNECTION_ID)] == null) {
          error("Field GradleProjectEntity#build should be initialized")
        }
      }
      if (!getEntityData().isBuildIdInitialized()) {
        error("Field GradleProjectEntity#buildId should be initialized")
      }
      if (!getEntityData().isNameInitialized()) {
        error("Field GradleProjectEntity#name should be initialized")
      }
      if (!getEntityData().isPathInitialized()) {
        error("Field GradleProjectEntity#path should be initialized")
      }
      if (!getEntityData().isIdentityPathInitialized()) {
        error("Field GradleProjectEntity#identityPath should be initialized")
      }
      if (!getEntityData().isUrlInitialized()) {
        error("Field GradleProjectEntity#url should be initialized")
      }
      if (!getEntityData().isLinkedProjectIdInitialized()) {
        error("Field GradleProjectEntity#linkedProjectId should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as GradleProjectEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.buildId != dataSource.buildId) this.buildId = dataSource.buildId
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.path != dataSource.path) this.path = dataSource.path
      if (this.identityPath != dataSource.identityPath) this.identityPath = dataSource.identityPath
      if (this.url != dataSource.url) this.url = dataSource.url
      if (this.linkedProjectId != dataSource.linkedProjectId) this.linkedProjectId = dataSource.linkedProjectId
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var build: GradleBuildEntityBuilder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(BUILD_CONNECTION_ID, this) as? GradleBuildEntityBuilder)
          ?: (this.entityLinks[EntityLink(false, BUILD_CONNECTION_ID)]!! as GradleBuildEntityBuilder)
        }
        else {
          this.entityLinks[EntityLink(false, BUILD_CONNECTION_ID)]!! as GradleBuildEntityBuilder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, BUILD_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, BUILD_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(BUILD_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, BUILD_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, BUILD_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, BUILD_CONNECTION_ID)] = value
        }
        changedProperty.add("build")
      }

    override var buildId: GradleBuildEntityId
      get() = getEntityData().buildId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).buildId = value
        changedProperty.add("buildId")

      }

    override var name: String
      get() = getEntityData().name
      set(value) {
        checkModificationAllowed()
        getEntityData(true).name = value
        changedProperty.add("name")
      }

    override var path: String
      get() = getEntityData().path
      set(value) {
        checkModificationAllowed()
        getEntityData(true).path = value
        changedProperty.add("path")
      }

    override var identityPath: String
      get() = getEntityData().identityPath
      set(value) {
        checkModificationAllowed()
        getEntityData(true).identityPath = value
        changedProperty.add("identityPath")
      }

    override var url: VirtualFileUrl
      get() = getEntityData().url
      set(value) {
        checkModificationAllowed()
        getEntityData(true).url = value
        changedProperty.add("url")
        val _diff = diff
        if (_diff != null) index(this, "url", value)
      }

    override var linkedProjectId: String
      get() = getEntityData().linkedProjectId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).linkedProjectId = value
        changedProperty.add("linkedProjectId")
      }

    override fun getEntityClass(): Class<GradleProjectEntity> = GradleProjectEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class GradleProjectEntityData : WorkspaceEntityData<GradleProjectEntity>(), SoftLinkable {
  lateinit var buildId: GradleBuildEntityId
  lateinit var name: String
  lateinit var path: String
  lateinit var identityPath: String
  lateinit var url: VirtualFileUrl
  lateinit var linkedProjectId: String

  internal fun isBuildIdInitialized(): Boolean = ::buildId.isInitialized
  internal fun isNameInitialized(): Boolean = ::name.isInitialized
  internal fun isPathInitialized(): Boolean = ::path.isInitialized
  internal fun isIdentityPathInitialized(): Boolean = ::identityPath.isInitialized
  internal fun isUrlInitialized(): Boolean = ::url.isInitialized
  internal fun isLinkedProjectIdInitialized(): Boolean = ::linkedProjectId.isInitialized

  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    result.add(buildId)
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    index.index(this, buildId)
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    // TODO verify logic
    val mutablePreviousSet = HashSet(prev)
    val removedItem_buildId = mutablePreviousSet.remove(buildId)
    if (!removedItem_buildId) {
      index.index(this, buildId)
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val buildId_data = if (buildId == oldLink) {
      changed = true
      newLink as GradleBuildEntityId
    }
    else {
      null
    }
    if (buildId_data != null) {
      buildId = buildId_data
    }
    return changed
  }

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<GradleProjectEntity> {
    val modifiable = GradleProjectEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): GradleProjectEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = GradleProjectEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("org.jetbrains.plugins.gradle.model.projectModel.GradleProjectEntity") as EntityMetadata
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return GradleProjectEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return GradleProjectEntity(buildId, name, path, identityPath, url, linkedProjectId, entitySource) {
      parents.filterIsInstance<GradleBuildEntityBuilder>().singleOrNull()?.let { this.build = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(GradleBuildEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as GradleProjectEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.buildId != other.buildId) return false
    if (this.name != other.name) return false
    if (this.path != other.path) return false
    if (this.identityPath != other.identityPath) return false
    if (this.url != other.url) return false
    if (this.linkedProjectId != other.linkedProjectId) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as GradleProjectEntityData

    if (this.buildId != other.buildId) return false
    if (this.name != other.name) return false
    if (this.path != other.path) return false
    if (this.identityPath != other.identityPath) return false
    if (this.url != other.url) return false
    if (this.linkedProjectId != other.linkedProjectId) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + buildId.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + path.hashCode()
    result = 31 * result + identityPath.hashCode()
    result = 31 * result + url.hashCode()
    result = 31 * result + linkedProjectId.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + buildId.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + path.hashCode()
    result = 31 * result + identityPath.hashCode()
    result = 31 * result + url.hashCode()
    result = 31 * result + linkedProjectId.hashCode()
    return result
  }
}
