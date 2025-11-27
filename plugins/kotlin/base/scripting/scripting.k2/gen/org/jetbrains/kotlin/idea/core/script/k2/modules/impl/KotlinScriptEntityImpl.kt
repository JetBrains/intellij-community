// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules.impl

import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.annotations.Default
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntityBuilder
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntityId
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptCompilationConfigurationEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.ScriptDiagnosticData

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(7)
@OptIn(WorkspaceEntityInternalApi::class)
internal class KotlinScriptEntityImpl(private val dataSource: KotlinScriptEntityData) : KotlinScriptEntity, WorkspaceEntityBase(
  dataSource) {

  private companion object {


    private val connections = listOf<ConnectionId>(
    )

  }

  override val virtualFileUrl: VirtualFileUrl
    get() {
      readField("virtualFileUrl")
      return dataSource.virtualFileUrl
    }

  override val dependencies: List<KotlinScriptLibraryEntityId>
    get() {
      readField("dependencies")
      return dataSource.dependencies
    }

  override val configuration: ScriptCompilationConfigurationEntity?
    get() {
      readField("configuration")
      return dataSource.configuration
    }

  override val sdkId: SdkId?
    get() {
      readField("sdkId")
      return dataSource.sdkId
    }

  override var reports: List<ScriptDiagnosticData> = dataSource.reports

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: KotlinScriptEntityData?) : ModifiableWorkspaceEntityBase<KotlinScriptEntity, KotlinScriptEntityData>(
    result), KotlinScriptEntityBuilder {
    internal constructor() : this(KotlinScriptEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity KotlinScriptEntity is already created in a different builder")
        }
      }

      this.diff = builder
      addToBuilder()
      this.id = getEntityData().createEntityId()
      // After adding entity data to the builder, we need to unbind it and move the control over entity data to builder
      // Builder may switch to snapshot at any moment and lock entity data to modification
      this.currentEntityData = null

      index(this, "virtualFileUrl", this.virtualFileUrl)
      // Process linked entities that are connected without a builder
      processLinkedEntities(builder)
      checkInitialization() // TODO uncomment and check failed tests
    }

    private fun checkInitialization() {
      val _diff = diff
      if (!getEntityData().isEntitySourceInitialized()) {
        error("Field WorkspaceEntity#entitySource should be initialized")
      }
      if (!getEntityData().isVirtualFileUrlInitialized()) {
        error("Field KotlinScriptEntity#virtualFileUrl should be initialized")
      }
      if (!getEntityData().isDependenciesInitialized()) {
        error("Field KotlinScriptEntity#dependencies should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_dependencies = getEntityData().dependencies
      if (collection_dependencies is MutableWorkspaceList<*>) {
        collection_dependencies.cleanModificationUpdateAction()
      }
      val collection_reports = getEntityData().reports
      if (collection_reports is MutableWorkspaceList<*>) {
        collection_reports.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as KotlinScriptEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.virtualFileUrl != dataSource.virtualFileUrl) this.virtualFileUrl = dataSource.virtualFileUrl
      if (this.dependencies != dataSource.dependencies) this.dependencies = dataSource.dependencies.toMutableList()
      if (this.configuration != dataSource?.configuration) this.configuration = dataSource.configuration
      if (this.sdkId != dataSource?.sdkId) this.sdkId = dataSource.sdkId
      if (this.reports != dataSource.reports) this.reports = dataSource.reports.toMutableList()
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var virtualFileUrl: VirtualFileUrl
      get() = getEntityData().virtualFileUrl
      set(value) {
        checkModificationAllowed()
        getEntityData(true).virtualFileUrl = value
        changedProperty.add("virtualFileUrl")
        val _diff = diff
        if (_diff != null) index(this, "virtualFileUrl", value)
      }

    private val dependenciesUpdater: (value: List<KotlinScriptLibraryEntityId>) -> Unit = { value ->

      changedProperty.add("dependencies")
    }
    override var dependencies: MutableList<KotlinScriptLibraryEntityId>
      get() {
        val collection_dependencies = getEntityData().dependencies
        if (collection_dependencies !is MutableWorkspaceList) return collection_dependencies
        if (diff == null || modifiable.get()) {
          collection_dependencies.setModificationUpdateAction(dependenciesUpdater)
        }
        else {
          collection_dependencies.cleanModificationUpdateAction()
        }
        return collection_dependencies
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).dependencies = value
        dependenciesUpdater.invoke(value)
      }

    override var configuration: ScriptCompilationConfigurationEntity?
      get() = getEntityData().configuration
      set(value) {
        checkModificationAllowed()
        getEntityData(true).configuration = value
        changedProperty.add("configuration")

      }

    override var sdkId: SdkId?
      get() = getEntityData().sdkId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).sdkId = value
        changedProperty.add("sdkId")

      }

    private val reportsUpdater: (value: List<ScriptDiagnosticData>) -> Unit = { value ->

      changedProperty.add("reports")
    }
    override var reports: MutableList<ScriptDiagnosticData>
      get() {
        val collection_reports = getEntityData().reports
        if (collection_reports !is MutableWorkspaceList) return collection_reports
        if (diff == null || modifiable.get()) {
          collection_reports.setModificationUpdateAction(reportsUpdater)
        }
        else {
          collection_reports.cleanModificationUpdateAction()
        }
        return collection_reports
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).reports = value
        reportsUpdater.invoke(value)
      }

    override fun getEntityClass(): Class<KotlinScriptEntity> = KotlinScriptEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class KotlinScriptEntityData : WorkspaceEntityData<KotlinScriptEntity>(), SoftLinkable {
  lateinit var virtualFileUrl: VirtualFileUrl
  lateinit var dependencies: MutableList<KotlinScriptLibraryEntityId>
  var configuration: ScriptCompilationConfigurationEntity? = null
  var sdkId: SdkId? = null
  var reports: MutableList<ScriptDiagnosticData> = listOf<ScriptDiagnosticData>().toMutableWorkspaceList()

  internal fun isVirtualFileUrlInitialized(): Boolean = ::virtualFileUrl.isInitialized
  internal fun isDependenciesInitialized(): Boolean = ::dependencies.isInitialized

  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    for (item in dependencies) {
      result.add(item)
    }
    val optionalLink_sdkId = sdkId
    if (optionalLink_sdkId != null) {
      result.add(optionalLink_sdkId)
    }
    for (item in reports) {
    }
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    for (item in dependencies) {
      index.index(this, item)
    }
    val optionalLink_sdkId = sdkId
    if (optionalLink_sdkId != null) {
      index.index(this, optionalLink_sdkId)
    }
    for (item in reports) {
    }
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    // TODO verify logic
    val mutablePreviousSet = HashSet(prev)
    for (item in dependencies) {
      val removedItem_item = mutablePreviousSet.remove(item)
      if (!removedItem_item) {
        index.index(this, item)
      }
    }
    val optionalLink_sdkId = sdkId
    if (optionalLink_sdkId != null) {
      val removedItem_optionalLink_sdkId = mutablePreviousSet.remove(optionalLink_sdkId)
      if (!removedItem_optionalLink_sdkId) {
        index.index(this, optionalLink_sdkId)
      }
    }
    for (item in reports) {
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val dependencies_data = dependencies.map {
      val it_data = if (it == oldLink) {
        changed = true
        newLink as KotlinScriptLibraryEntityId
      }
      else {
        null
      }
      if (it_data != null) {
        it_data
      }
      else {
        it
      }
    }
    if (dependencies_data != null) {
      dependencies = dependencies_data as MutableList<KotlinScriptLibraryEntityId>
    }
    var sdkId_data_optional = if (sdkId != null) {
      val sdkId___data = if (sdkId!! == oldLink) {
        changed = true
        newLink as SdkId
      }
      else {
        null
      }
      sdkId___data
    }
    else {
      null
    }
    if (sdkId_data_optional != null) {
      sdkId = sdkId_data_optional
    }
    return changed
  }

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntityBuilder<KotlinScriptEntity> {
    val modifiable = KotlinScriptEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): KotlinScriptEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = KotlinScriptEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity") as EntityMetadata
  }

  override fun clone(): KotlinScriptEntityData {
    val clonedEntity = super.clone()
    clonedEntity as KotlinScriptEntityData
    clonedEntity.dependencies = clonedEntity.dependencies.toMutableWorkspaceList()
    clonedEntity.reports = clonedEntity.reports.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return KotlinScriptEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntityBuilder<*>>): WorkspaceEntityBuilder<*> {
    return KotlinScriptEntity(virtualFileUrl, dependencies, entitySource) {
      this.configuration = this@KotlinScriptEntityData.configuration
      this.sdkId = this@KotlinScriptEntityData.sdkId
      this.reports = this@KotlinScriptEntityData.reports.toMutableWorkspaceList()
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as KotlinScriptEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.virtualFileUrl != other.virtualFileUrl) return false
    if (this.dependencies != other.dependencies) return false
    if (this.configuration != other.configuration) return false
    if (this.sdkId != other.sdkId) return false
    if (this.reports != other.reports) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as KotlinScriptEntityData

    if (this.virtualFileUrl != other.virtualFileUrl) return false
    if (this.dependencies != other.dependencies) return false
    if (this.configuration != other.configuration) return false
    if (this.sdkId != other.sdkId) return false
    if (this.reports != other.reports) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + virtualFileUrl.hashCode()
    result = 31 * result + dependencies.hashCode()
    result = 31 * result + configuration.hashCode()
    result = 31 * result + sdkId.hashCode()
    result = 31 * result + reports.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + virtualFileUrl.hashCode()
    result = 31 * result + dependencies.hashCode()
    result = 31 * result + configuration.hashCode()
    result = 31 * result + sdkId.hashCode()
    result = 31 * result + reports.hashCode()
    return result
  }
}
