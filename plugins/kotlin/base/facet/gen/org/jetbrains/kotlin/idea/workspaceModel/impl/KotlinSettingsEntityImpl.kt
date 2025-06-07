// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.workspaceModel.impl

import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.ModuleSettingsFacetBridgeEntity
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityType
import com.intellij.platform.workspace.storage.GeneratedCodeApiVersion
import com.intellij.platform.workspace.storage.GeneratedCodeImplVersion
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.SymbolicEntityId
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityInternalApi
import com.intellij.platform.workspace.storage.annotations.Child
import com.intellij.platform.workspace.storage.impl.EntityLink
import com.intellij.platform.workspace.storage.impl.ModifiableWorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.SoftLinkable
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityData
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.MutableWorkspaceSet
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceList
import com.intellij.platform.workspace.storage.impl.containers.toMutableWorkspaceSet
import com.intellij.platform.workspace.storage.impl.extractOneToManyParent
import com.intellij.platform.workspace.storage.impl.indices.WorkspaceMutableIndex
import com.intellij.platform.workspace.storage.impl.updateOneToManyParentOfChild
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentation
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.metadata.model.EntityMetadata
import com.intellij.util.descriptors.ConfigFileItem
import org.jetbrains.kotlin.config.KotlinModuleKind
import org.jetbrains.kotlin.idea.workspaceModel.CompilerSettingsData
import org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity
import org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsId

@GeneratedCodeApiVersion(3)
@GeneratedCodeImplVersion(6)
@OptIn(WorkspaceEntityInternalApi::class)
internal class KotlinSettingsEntityImpl(private val dataSource: KotlinSettingsEntityData) : KotlinSettingsEntity,
                                                                                            WorkspaceEntityBase(dataSource) {

  private companion object {
    internal val MODULE_CONNECTION_ID: ConnectionId =
      ConnectionId.create(ModuleEntity::class.java, KotlinSettingsEntity::class.java, ConnectionId.ConnectionType.ONE_TO_MANY, false)

    private val connections = listOf<ConnectionId>(
      MODULE_CONNECTION_ID,
    )

  }

  override val symbolicId: KotlinSettingsId = super.symbolicId

  override val moduleId: ModuleId
    get() {
      readField("moduleId")
      return dataSource.moduleId
    }

  override val name: String
    get() {
      readField("name")
      return dataSource.name
    }

  override val sourceRoots: List<String>
    get() {
      readField("sourceRoots")
      return dataSource.sourceRoots
    }

  override val configFileItems: List<ConfigFileItem>
    get() {
      readField("configFileItems")
      return dataSource.configFileItems
    }

  override val module: ModuleEntity
    get() = snapshot.extractOneToManyParent(MODULE_CONNECTION_ID, this)!!

  override val useProjectSettings: Boolean
    get() {
      readField("useProjectSettings")
      return dataSource.useProjectSettings
    }
  override val implementedModuleNames: List<String>
    get() {
      readField("implementedModuleNames")
      return dataSource.implementedModuleNames
    }

  override val dependsOnModuleNames: List<String>
    get() {
      readField("dependsOnModuleNames")
      return dataSource.dependsOnModuleNames
    }

  override val additionalVisibleModuleNames: Set<String>
    get() {
      readField("additionalVisibleModuleNames")
      return dataSource.additionalVisibleModuleNames
    }

  override val productionOutputPath: String?
    get() {
      readField("productionOutputPath")
      return dataSource.productionOutputPath
    }

  override val testOutputPath: String?
    get() {
      readField("testOutputPath")
      return dataSource.testOutputPath
    }

  override val sourceSetNames: List<String>
    get() {
      readField("sourceSetNames")
      return dataSource.sourceSetNames
    }

  override val isTestModule: Boolean
    get() {
      readField("isTestModule")
      return dataSource.isTestModule
    }
  override val externalProjectId: String
    get() {
      readField("externalProjectId")
      return dataSource.externalProjectId
    }

  override val isHmppEnabled: Boolean
    get() {
      readField("isHmppEnabled")
      return dataSource.isHmppEnabled
    }
  override val pureKotlinSourceFolders: List<String>
    get() {
      readField("pureKotlinSourceFolders")
      return dataSource.pureKotlinSourceFolders
    }

  override val kind: KotlinModuleKind
    get() {
      readField("kind")
      return dataSource.kind
    }

  override val compilerArguments: String?
    get() {
      readField("compilerArguments")
      return dataSource.compilerArguments
    }

  override val compilerSettings: CompilerSettingsData?
    get() {
      readField("compilerSettings")
      return dataSource.compilerSettings
    }

  override val targetPlatform: String?
    get() {
      readField("targetPlatform")
      return dataSource.targetPlatform
    }

  override val externalSystemRunTasks: List<String>
    get() {
      readField("externalSystemRunTasks")
      return dataSource.externalSystemRunTasks
    }

  override val version: Int
    get() {
      readField("version")
      return dataSource.version
    }
  override val flushNeeded: Boolean
    get() {
      readField("flushNeeded")
      return dataSource.flushNeeded
    }

  override val entitySource: EntitySource
    get() {
      readField("entitySource")
      return dataSource.entitySource
    }

  override fun connectionIdList(): List<ConnectionId> {
    return connections
  }


  internal class Builder(result: KotlinSettingsEntityData?) :
    ModifiableWorkspaceEntityBase<KotlinSettingsEntity, KotlinSettingsEntityData>(result), KotlinSettingsEntity.Builder {
    internal constructor() : this(KotlinSettingsEntityData())

    override fun applyToBuilder(builder: MutableEntityStorage) {
      if (this.diff != null) {
        if (existsInBuilder(builder)) {
          this.diff = builder
          return
        }
        else {
          error("Entity KotlinSettingsEntity is already created in a different builder")
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
      if (!getEntityData().isModuleIdInitialized()) {
        error("Field ModuleSettingsFacetBridgeEntity#moduleId should be initialized")
      }
      if (!getEntityData().isNameInitialized()) {
        error("Field ModuleSettingsFacetBridgeEntity#name should be initialized")
      }
      if (!getEntityData().isSourceRootsInitialized()) {
        error("Field KotlinSettingsEntity#sourceRoots should be initialized")
      }
      if (!getEntityData().isConfigFileItemsInitialized()) {
        error("Field KotlinSettingsEntity#configFileItems should be initialized")
      }
      if (_diff != null) {
        if (_diff.extractOneToManyParent<WorkspaceEntityBase>(MODULE_CONNECTION_ID, this) == null) {
          error("Field KotlinSettingsEntity#module should be initialized")
        }
      }
      else {
        if (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] == null) {
          error("Field KotlinSettingsEntity#module should be initialized")
        }
      }
      if (!getEntityData().isImplementedModuleNamesInitialized()) {
        error("Field KotlinSettingsEntity#implementedModuleNames should be initialized")
      }
      if (!getEntityData().isDependsOnModuleNamesInitialized()) {
        error("Field KotlinSettingsEntity#dependsOnModuleNames should be initialized")
      }
      if (!getEntityData().isAdditionalVisibleModuleNamesInitialized()) {
        error("Field KotlinSettingsEntity#additionalVisibleModuleNames should be initialized")
      }
      if (!getEntityData().isSourceSetNamesInitialized()) {
        error("Field KotlinSettingsEntity#sourceSetNames should be initialized")
      }
      if (!getEntityData().isExternalProjectIdInitialized()) {
        error("Field KotlinSettingsEntity#externalProjectId should be initialized")
      }
      if (!getEntityData().isPureKotlinSourceFoldersInitialized()) {
        error("Field KotlinSettingsEntity#pureKotlinSourceFolders should be initialized")
      }
      if (!getEntityData().isKindInitialized()) {
        error("Field KotlinSettingsEntity#kind should be initialized")
      }
      if (!getEntityData().isExternalSystemRunTasksInitialized()) {
        error("Field KotlinSettingsEntity#externalSystemRunTasks should be initialized")
      }
    }

    override fun connectionIdList(): List<ConnectionId> {
      return connections
    }

    override fun afterModification() {
      val collection_sourceRoots = getEntityData().sourceRoots
      if (collection_sourceRoots is MutableWorkspaceList<*>) {
        collection_sourceRoots.cleanModificationUpdateAction()
      }
      val collection_configFileItems = getEntityData().configFileItems
      if (collection_configFileItems is MutableWorkspaceList<*>) {
        collection_configFileItems.cleanModificationUpdateAction()
      }
      val collection_implementedModuleNames = getEntityData().implementedModuleNames
      if (collection_implementedModuleNames is MutableWorkspaceList<*>) {
        collection_implementedModuleNames.cleanModificationUpdateAction()
      }
      val collection_dependsOnModuleNames = getEntityData().dependsOnModuleNames
      if (collection_dependsOnModuleNames is MutableWorkspaceList<*>) {
        collection_dependsOnModuleNames.cleanModificationUpdateAction()
      }
      val collection_additionalVisibleModuleNames = getEntityData().additionalVisibleModuleNames
      if (collection_additionalVisibleModuleNames is MutableWorkspaceSet<*>) {
        collection_additionalVisibleModuleNames.cleanModificationUpdateAction()
      }
      val collection_sourceSetNames = getEntityData().sourceSetNames
      if (collection_sourceSetNames is MutableWorkspaceList<*>) {
        collection_sourceSetNames.cleanModificationUpdateAction()
      }
      val collection_pureKotlinSourceFolders = getEntityData().pureKotlinSourceFolders
      if (collection_pureKotlinSourceFolders is MutableWorkspaceList<*>) {
        collection_pureKotlinSourceFolders.cleanModificationUpdateAction()
      }
      val collection_externalSystemRunTasks = getEntityData().externalSystemRunTasks
      if (collection_externalSystemRunTasks is MutableWorkspaceList<*>) {
        collection_externalSystemRunTasks.cleanModificationUpdateAction()
      }
    }

    // Relabeling code, move information from dataSource to this builder
    override fun relabel(dataSource: WorkspaceEntity, parents: Set<WorkspaceEntity>?) {
      dataSource as KotlinSettingsEntity
      if (this.entitySource != dataSource.entitySource) this.entitySource = dataSource.entitySource
      if (this.moduleId != dataSource.moduleId) this.moduleId = dataSource.moduleId
      if (this.name != dataSource.name) this.name = dataSource.name
      if (this.sourceRoots != dataSource.sourceRoots) this.sourceRoots = dataSource.sourceRoots.toMutableList()
      if (this.configFileItems != dataSource.configFileItems) this.configFileItems = dataSource.configFileItems.toMutableList()
      if (this.useProjectSettings != dataSource.useProjectSettings) this.useProjectSettings = dataSource.useProjectSettings
      if (this.implementedModuleNames != dataSource.implementedModuleNames) this.implementedModuleNames =
        dataSource.implementedModuleNames.toMutableList()
      if (this.dependsOnModuleNames != dataSource.dependsOnModuleNames) this.dependsOnModuleNames =
        dataSource.dependsOnModuleNames.toMutableList()
      if (this.additionalVisibleModuleNames != dataSource.additionalVisibleModuleNames) this.additionalVisibleModuleNames =
        dataSource.additionalVisibleModuleNames.toMutableSet()
      if (this.productionOutputPath != dataSource?.productionOutputPath) this.productionOutputPath = dataSource.productionOutputPath
      if (this.testOutputPath != dataSource?.testOutputPath) this.testOutputPath = dataSource.testOutputPath
      if (this.sourceSetNames != dataSource.sourceSetNames) this.sourceSetNames = dataSource.sourceSetNames.toMutableList()
      if (this.isTestModule != dataSource.isTestModule) this.isTestModule = dataSource.isTestModule
      if (this.externalProjectId != dataSource.externalProjectId) this.externalProjectId = dataSource.externalProjectId
      if (this.isHmppEnabled != dataSource.isHmppEnabled) this.isHmppEnabled = dataSource.isHmppEnabled
      if (this.pureKotlinSourceFolders != dataSource.pureKotlinSourceFolders) this.pureKotlinSourceFolders =
        dataSource.pureKotlinSourceFolders.toMutableList()
      if (this.kind != dataSource.kind) this.kind = dataSource.kind
      if (this.compilerArguments != dataSource?.compilerArguments) this.compilerArguments = dataSource.compilerArguments
      if (this.compilerSettings != dataSource?.compilerSettings) this.compilerSettings = dataSource.compilerSettings
      if (this.targetPlatform != dataSource?.targetPlatform) this.targetPlatform = dataSource.targetPlatform
      if (this.externalSystemRunTasks != dataSource.externalSystemRunTasks) this.externalSystemRunTasks =
        dataSource.externalSystemRunTasks.toMutableList()
      if (this.version != dataSource.version) this.version = dataSource.version
      if (this.flushNeeded != dataSource.flushNeeded) this.flushNeeded = dataSource.flushNeeded
      updateChildToParentReferences(parents)
    }


    override var entitySource: EntitySource
      get() = getEntityData().entitySource
      set(value) {
        checkModificationAllowed()
        getEntityData(true).entitySource = value
        changedProperty.add("entitySource")

      }

    override var moduleId: ModuleId
      get() = getEntityData().moduleId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).moduleId = value
        changedProperty.add("moduleId")

      }

    override var name: String
      get() = getEntityData().name
      set(value) {
        checkModificationAllowed()
        getEntityData(true).name = value
        changedProperty.add("name")
      }

    private val sourceRootsUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("sourceRoots")
    }
    override var sourceRoots: MutableList<String>
      get() {
        val collection_sourceRoots = getEntityData().sourceRoots
        if (collection_sourceRoots !is MutableWorkspaceList) return collection_sourceRoots
        if (diff == null || modifiable.get()) {
          collection_sourceRoots.setModificationUpdateAction(sourceRootsUpdater)
        }
        else {
          collection_sourceRoots.cleanModificationUpdateAction()
        }
        return collection_sourceRoots
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).sourceRoots = value
        sourceRootsUpdater.invoke(value)
      }

    private val configFileItemsUpdater: (value: List<ConfigFileItem>) -> Unit = { value ->

      changedProperty.add("configFileItems")
    }
    override var configFileItems: MutableList<ConfigFileItem>
      get() {
        val collection_configFileItems = getEntityData().configFileItems
        if (collection_configFileItems !is MutableWorkspaceList) return collection_configFileItems
        if (diff == null || modifiable.get()) {
          collection_configFileItems.setModificationUpdateAction(configFileItemsUpdater)
        }
        else {
          collection_configFileItems.cleanModificationUpdateAction()
        }
        return collection_configFileItems
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).configFileItems = value
        configFileItemsUpdater.invoke(value)
      }

    override var module: ModuleEntity.Builder
      get() {
        val _diff = diff
        return if (_diff != null) {
          @OptIn(EntityStorageInstrumentationApi::class)
          ((_diff as MutableEntityStorageInstrumentation).getParentBuilder(MODULE_CONNECTION_ID, this) as? ModuleEntity.Builder)
          ?: (this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity.Builder)
        }
        else {
          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)]!! as ModuleEntity.Builder
        }
      }
      set(value) {
        checkModificationAllowed()
        val _diff = diff
        if (_diff != null && value is ModifiableWorkspaceEntityBase<*, *> && value.diff == null) {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable
          _diff.addEntity(value as ModifiableWorkspaceEntityBase<WorkspaceEntity, *>)
        }
        if (_diff != null && (value !is ModifiableWorkspaceEntityBase<*, *> || value.diff != null)) {
          _diff.updateOneToManyParentOfChild(MODULE_CONNECTION_ID, this, value)
        }
        else {
          // Setting backref of the list
          if (value is ModifiableWorkspaceEntityBase<*, *>) {
            val data = (value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] as? List<Any> ?: emptyList()) + this
            value.entityLinks[EntityLink(true, MODULE_CONNECTION_ID)] = data
          }
          // else you're attaching a new entity to an existing entity that is not modifiable

          this.entityLinks[EntityLink(false, MODULE_CONNECTION_ID)] = value
        }
        changedProperty.add("module")
      }

    override var useProjectSettings: Boolean
      get() = getEntityData().useProjectSettings
      set(value) {
        checkModificationAllowed()
        getEntityData(true).useProjectSettings = value
        changedProperty.add("useProjectSettings")
      }

    private val implementedModuleNamesUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("implementedModuleNames")
    }
    override var implementedModuleNames: MutableList<String>
      get() {
        val collection_implementedModuleNames = getEntityData().implementedModuleNames
        if (collection_implementedModuleNames !is MutableWorkspaceList) return collection_implementedModuleNames
        if (diff == null || modifiable.get()) {
          collection_implementedModuleNames.setModificationUpdateAction(implementedModuleNamesUpdater)
        }
        else {
          collection_implementedModuleNames.cleanModificationUpdateAction()
        }
        return collection_implementedModuleNames
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).implementedModuleNames = value
        implementedModuleNamesUpdater.invoke(value)
      }

    private val dependsOnModuleNamesUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("dependsOnModuleNames")
    }
    override var dependsOnModuleNames: MutableList<String>
      get() {
        val collection_dependsOnModuleNames = getEntityData().dependsOnModuleNames
        if (collection_dependsOnModuleNames !is MutableWorkspaceList) return collection_dependsOnModuleNames
        if (diff == null || modifiable.get()) {
          collection_dependsOnModuleNames.setModificationUpdateAction(dependsOnModuleNamesUpdater)
        }
        else {
          collection_dependsOnModuleNames.cleanModificationUpdateAction()
        }
        return collection_dependsOnModuleNames
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).dependsOnModuleNames = value
        dependsOnModuleNamesUpdater.invoke(value)
      }

    private val additionalVisibleModuleNamesUpdater: (value: Set<String>) -> Unit = { value ->

      changedProperty.add("additionalVisibleModuleNames")
    }
    override var additionalVisibleModuleNames: MutableSet<String>
      get() {
        val collection_additionalVisibleModuleNames = getEntityData().additionalVisibleModuleNames
        if (collection_additionalVisibleModuleNames !is MutableWorkspaceSet) return collection_additionalVisibleModuleNames
        if (diff == null || modifiable.get()) {
          collection_additionalVisibleModuleNames.setModificationUpdateAction(additionalVisibleModuleNamesUpdater)
        }
        else {
          collection_additionalVisibleModuleNames.cleanModificationUpdateAction()
        }
        return collection_additionalVisibleModuleNames
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).additionalVisibleModuleNames = value
        additionalVisibleModuleNamesUpdater.invoke(value)
      }

    override var productionOutputPath: String?
      get() = getEntityData().productionOutputPath
      set(value) {
        checkModificationAllowed()
        getEntityData(true).productionOutputPath = value
        changedProperty.add("productionOutputPath")
      }

    override var testOutputPath: String?
      get() = getEntityData().testOutputPath
      set(value) {
        checkModificationAllowed()
        getEntityData(true).testOutputPath = value
        changedProperty.add("testOutputPath")
      }

    private val sourceSetNamesUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("sourceSetNames")
    }
    override var sourceSetNames: MutableList<String>
      get() {
        val collection_sourceSetNames = getEntityData().sourceSetNames
        if (collection_sourceSetNames !is MutableWorkspaceList) return collection_sourceSetNames
        if (diff == null || modifiable.get()) {
          collection_sourceSetNames.setModificationUpdateAction(sourceSetNamesUpdater)
        }
        else {
          collection_sourceSetNames.cleanModificationUpdateAction()
        }
        return collection_sourceSetNames
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).sourceSetNames = value
        sourceSetNamesUpdater.invoke(value)
      }

    override var isTestModule: Boolean
      get() = getEntityData().isTestModule
      set(value) {
        checkModificationAllowed()
        getEntityData(true).isTestModule = value
        changedProperty.add("isTestModule")
      }

    override var externalProjectId: String
      get() = getEntityData().externalProjectId
      set(value) {
        checkModificationAllowed()
        getEntityData(true).externalProjectId = value
        changedProperty.add("externalProjectId")
      }

    override var isHmppEnabled: Boolean
      get() = getEntityData().isHmppEnabled
      set(value) {
        checkModificationAllowed()
        getEntityData(true).isHmppEnabled = value
        changedProperty.add("isHmppEnabled")
      }

    private val pureKotlinSourceFoldersUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("pureKotlinSourceFolders")
    }
    override var pureKotlinSourceFolders: MutableList<String>
      get() {
        val collection_pureKotlinSourceFolders = getEntityData().pureKotlinSourceFolders
        if (collection_pureKotlinSourceFolders !is MutableWorkspaceList) return collection_pureKotlinSourceFolders
        if (diff == null || modifiable.get()) {
          collection_pureKotlinSourceFolders.setModificationUpdateAction(pureKotlinSourceFoldersUpdater)
        }
        else {
          collection_pureKotlinSourceFolders.cleanModificationUpdateAction()
        }
        return collection_pureKotlinSourceFolders
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).pureKotlinSourceFolders = value
        pureKotlinSourceFoldersUpdater.invoke(value)
      }

    override var kind: KotlinModuleKind
      get() = getEntityData().kind
      set(value) {
        checkModificationAllowed()
        getEntityData(true).kind = value
        changedProperty.add("kind")

      }

    override var compilerArguments: String?
      get() = getEntityData().compilerArguments
      set(value) {
        checkModificationAllowed()
        getEntityData(true).compilerArguments = value
        changedProperty.add("compilerArguments")
      }

    override var compilerSettings: CompilerSettingsData?
      get() = getEntityData().compilerSettings
      set(value) {
        checkModificationAllowed()
        getEntityData(true).compilerSettings = value
        changedProperty.add("compilerSettings")

      }

    override var targetPlatform: String?
      get() = getEntityData().targetPlatform
      set(value) {
        checkModificationAllowed()
        getEntityData(true).targetPlatform = value
        changedProperty.add("targetPlatform")
      }

    private val externalSystemRunTasksUpdater: (value: List<String>) -> Unit = { value ->

      changedProperty.add("externalSystemRunTasks")
    }
    override var externalSystemRunTasks: MutableList<String>
      get() {
        val collection_externalSystemRunTasks = getEntityData().externalSystemRunTasks
        if (collection_externalSystemRunTasks !is MutableWorkspaceList) return collection_externalSystemRunTasks
        if (diff == null || modifiable.get()) {
          collection_externalSystemRunTasks.setModificationUpdateAction(externalSystemRunTasksUpdater)
        }
        else {
          collection_externalSystemRunTasks.cleanModificationUpdateAction()
        }
        return collection_externalSystemRunTasks
      }
      set(value) {
        checkModificationAllowed()
        getEntityData(true).externalSystemRunTasks = value
        externalSystemRunTasksUpdater.invoke(value)
      }

    override var version: Int
      get() = getEntityData().version
      set(value) {
        checkModificationAllowed()
        getEntityData(true).version = value
        changedProperty.add("version")
      }

    override var flushNeeded: Boolean
      get() = getEntityData().flushNeeded
      set(value) {
        checkModificationAllowed()
        getEntityData(true).flushNeeded = value
        changedProperty.add("flushNeeded")
      }

    override fun getEntityClass(): Class<KotlinSettingsEntity> = KotlinSettingsEntity::class.java
  }
}

@OptIn(WorkspaceEntityInternalApi::class)
internal class KotlinSettingsEntityData : WorkspaceEntityData<KotlinSettingsEntity>(), SoftLinkable {
  lateinit var moduleId: ModuleId
  lateinit var name: String
  lateinit var sourceRoots: MutableList<String>
  lateinit var configFileItems: MutableList<ConfigFileItem>
  var useProjectSettings: Boolean = false
  lateinit var implementedModuleNames: MutableList<String>
  lateinit var dependsOnModuleNames: MutableList<String>
  lateinit var additionalVisibleModuleNames: MutableSet<String>
  var productionOutputPath: String? = null
  var testOutputPath: String? = null
  lateinit var sourceSetNames: MutableList<String>
  var isTestModule: Boolean = false
  lateinit var externalProjectId: String
  var isHmppEnabled: Boolean = false
  lateinit var pureKotlinSourceFolders: MutableList<String>
  lateinit var kind: KotlinModuleKind
  var compilerArguments: String? = null
  var compilerSettings: CompilerSettingsData? = null
  var targetPlatform: String? = null
  lateinit var externalSystemRunTasks: MutableList<String>
  var version: Int = 0
  var flushNeeded: Boolean = false

  internal fun isModuleIdInitialized(): Boolean = ::moduleId.isInitialized
  internal fun isNameInitialized(): Boolean = ::name.isInitialized
  internal fun isSourceRootsInitialized(): Boolean = ::sourceRoots.isInitialized
  internal fun isConfigFileItemsInitialized(): Boolean = ::configFileItems.isInitialized

  internal fun isImplementedModuleNamesInitialized(): Boolean = ::implementedModuleNames.isInitialized
  internal fun isDependsOnModuleNamesInitialized(): Boolean = ::dependsOnModuleNames.isInitialized
  internal fun isAdditionalVisibleModuleNamesInitialized(): Boolean = ::additionalVisibleModuleNames.isInitialized
  internal fun isSourceSetNamesInitialized(): Boolean = ::sourceSetNames.isInitialized

  internal fun isExternalProjectIdInitialized(): Boolean = ::externalProjectId.isInitialized

  internal fun isPureKotlinSourceFoldersInitialized(): Boolean = ::pureKotlinSourceFolders.isInitialized
  internal fun isKindInitialized(): Boolean = ::kind.isInitialized
  internal fun isExternalSystemRunTasksInitialized(): Boolean = ::externalSystemRunTasks.isInitialized


  override fun getLinks(): Set<SymbolicEntityId<*>> {
    val result = HashSet<SymbolicEntityId<*>>()
    result.add(moduleId)
    for (item in sourceRoots) {
    }
    for (item in configFileItems) {
    }
    for (item in implementedModuleNames) {
    }
    for (item in dependsOnModuleNames) {
    }
    for (item in additionalVisibleModuleNames) {
    }
    for (item in sourceSetNames) {
    }
    for (item in pureKotlinSourceFolders) {
    }
    for (item in externalSystemRunTasks) {
    }
    return result
  }

  override fun index(index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    index.index(this, moduleId)
    for (item in sourceRoots) {
    }
    for (item in configFileItems) {
    }
    for (item in implementedModuleNames) {
    }
    for (item in dependsOnModuleNames) {
    }
    for (item in additionalVisibleModuleNames) {
    }
    for (item in sourceSetNames) {
    }
    for (item in pureKotlinSourceFolders) {
    }
    for (item in externalSystemRunTasks) {
    }
  }

  override fun updateLinksIndex(prev: Set<SymbolicEntityId<*>>, index: WorkspaceMutableIndex<SymbolicEntityId<*>>) {
    // TODO verify logic
    val mutablePreviousSet = HashSet(prev)
    val removedItem_moduleId = mutablePreviousSet.remove(moduleId)
    if (!removedItem_moduleId) {
      index.index(this, moduleId)
    }
    for (item in sourceRoots) {
    }
    for (item in configFileItems) {
    }
    for (item in implementedModuleNames) {
    }
    for (item in dependsOnModuleNames) {
    }
    for (item in additionalVisibleModuleNames) {
    }
    for (item in sourceSetNames) {
    }
    for (item in pureKotlinSourceFolders) {
    }
    for (item in externalSystemRunTasks) {
    }
    for (removed in mutablePreviousSet) {
      index.remove(this, removed)
    }
  }

  override fun updateLink(oldLink: SymbolicEntityId<*>, newLink: SymbolicEntityId<*>): Boolean {
    var changed = false
    val moduleId_data = if (moduleId == oldLink) {
      changed = true
      newLink as ModuleId
    }
    else {
      null
    }
    if (moduleId_data != null) {
      moduleId = moduleId_data
    }
    return changed
  }

  override fun wrapAsModifiable(diff: MutableEntityStorage): WorkspaceEntity.Builder<KotlinSettingsEntity> {
    val modifiable = KotlinSettingsEntityImpl.Builder(null)
    modifiable.diff = diff
    modifiable.id = createEntityId()
    return modifiable
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun createEntity(snapshot: EntityStorageInstrumentation): KotlinSettingsEntity {
    val entityId = createEntityId()
    return snapshot.initializeEntity(entityId) {
      val entity = KotlinSettingsEntityImpl(this)
      entity.snapshot = snapshot
      entity.id = entityId
      entity
    }
  }

  override fun getMetadata(): EntityMetadata {
    return MetadataStorageImpl.getMetadataByTypeFqn("org.jetbrains.kotlin.idea.workspaceModel.KotlinSettingsEntity") as EntityMetadata
  }

  override fun clone(): KotlinSettingsEntityData {
    val clonedEntity = super.clone()
    clonedEntity as KotlinSettingsEntityData
    clonedEntity.sourceRoots = clonedEntity.sourceRoots.toMutableWorkspaceList()
    clonedEntity.configFileItems = clonedEntity.configFileItems.toMutableWorkspaceList()
    clonedEntity.implementedModuleNames = clonedEntity.implementedModuleNames.toMutableWorkspaceList()
    clonedEntity.dependsOnModuleNames = clonedEntity.dependsOnModuleNames.toMutableWorkspaceList()
    clonedEntity.additionalVisibleModuleNames = clonedEntity.additionalVisibleModuleNames.toMutableWorkspaceSet()
    clonedEntity.sourceSetNames = clonedEntity.sourceSetNames.toMutableWorkspaceList()
    clonedEntity.pureKotlinSourceFolders = clonedEntity.pureKotlinSourceFolders.toMutableWorkspaceList()
    clonedEntity.externalSystemRunTasks = clonedEntity.externalSystemRunTasks.toMutableWorkspaceList()
    return clonedEntity
  }

  override fun getEntityInterface(): Class<out WorkspaceEntity> {
    return KotlinSettingsEntity::class.java
  }

  override fun createDetachedEntity(parents: List<WorkspaceEntity.Builder<*>>): WorkspaceEntity.Builder<*> {
    return KotlinSettingsEntity(
      moduleId, name, sourceRoots, configFileItems, useProjectSettings, implementedModuleNames, dependsOnModuleNames,
      additionalVisibleModuleNames, sourceSetNames, isTestModule, externalProjectId, isHmppEnabled, pureKotlinSourceFolders, kind,
      externalSystemRunTasks, version, flushNeeded, entitySource
    ) {
      this.productionOutputPath = this@KotlinSettingsEntityData.productionOutputPath
      this.testOutputPath = this@KotlinSettingsEntityData.testOutputPath
      this.compilerArguments = this@KotlinSettingsEntityData.compilerArguments
      this.compilerSettings = this@KotlinSettingsEntityData.compilerSettings
      this.targetPlatform = this@KotlinSettingsEntityData.targetPlatform
      parents.filterIsInstance<ModuleEntity.Builder>().singleOrNull()?.let { this.module = it }
    }
  }

  override fun getRequiredParents(): List<Class<out WorkspaceEntity>> {
    val res = mutableListOf<Class<out WorkspaceEntity>>()
    res.add(ModuleEntity::class.java)
    return res
  }

  override fun equals(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as KotlinSettingsEntityData

    if (this.entitySource != other.entitySource) return false
    if (this.moduleId != other.moduleId) return false
    if (this.name != other.name) return false
    if (this.sourceRoots != other.sourceRoots) return false
    if (this.configFileItems != other.configFileItems) return false
    if (this.useProjectSettings != other.useProjectSettings) return false
    if (this.implementedModuleNames != other.implementedModuleNames) return false
    if (this.dependsOnModuleNames != other.dependsOnModuleNames) return false
    if (this.additionalVisibleModuleNames != other.additionalVisibleModuleNames) return false
    if (this.productionOutputPath != other.productionOutputPath) return false
    if (this.testOutputPath != other.testOutputPath) return false
    if (this.sourceSetNames != other.sourceSetNames) return false
    if (this.isTestModule != other.isTestModule) return false
    if (this.externalProjectId != other.externalProjectId) return false
    if (this.isHmppEnabled != other.isHmppEnabled) return false
    if (this.pureKotlinSourceFolders != other.pureKotlinSourceFolders) return false
    if (this.kind != other.kind) return false
    if (this.compilerArguments != other.compilerArguments) return false
    if (this.compilerSettings != other.compilerSettings) return false
    if (this.targetPlatform != other.targetPlatform) return false
    if (this.externalSystemRunTasks != other.externalSystemRunTasks) return false
    if (this.version != other.version) return false
    if (this.flushNeeded != other.flushNeeded) return false
    return true
  }

  override fun equalsIgnoringEntitySource(other: Any?): Boolean {
    if (other == null) return false
    if (this.javaClass != other.javaClass) return false

    other as KotlinSettingsEntityData

    if (this.moduleId != other.moduleId) return false
    if (this.name != other.name) return false
    if (this.sourceRoots != other.sourceRoots) return false
    if (this.configFileItems != other.configFileItems) return false
    if (this.useProjectSettings != other.useProjectSettings) return false
    if (this.implementedModuleNames != other.implementedModuleNames) return false
    if (this.dependsOnModuleNames != other.dependsOnModuleNames) return false
    if (this.additionalVisibleModuleNames != other.additionalVisibleModuleNames) return false
    if (this.productionOutputPath != other.productionOutputPath) return false
    if (this.testOutputPath != other.testOutputPath) return false
    if (this.sourceSetNames != other.sourceSetNames) return false
    if (this.isTestModule != other.isTestModule) return false
    if (this.externalProjectId != other.externalProjectId) return false
    if (this.isHmppEnabled != other.isHmppEnabled) return false
    if (this.pureKotlinSourceFolders != other.pureKotlinSourceFolders) return false
    if (this.kind != other.kind) return false
    if (this.compilerArguments != other.compilerArguments) return false
    if (this.compilerSettings != other.compilerSettings) return false
    if (this.targetPlatform != other.targetPlatform) return false
    if (this.externalSystemRunTasks != other.externalSystemRunTasks) return false
    if (this.version != other.version) return false
    if (this.flushNeeded != other.flushNeeded) return false
    return true
  }

  override fun hashCode(): Int {
    var result = entitySource.hashCode()
    result = 31 * result + moduleId.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + sourceRoots.hashCode()
    result = 31 * result + configFileItems.hashCode()
    result = 31 * result + useProjectSettings.hashCode()
    result = 31 * result + implementedModuleNames.hashCode()
    result = 31 * result + dependsOnModuleNames.hashCode()
    result = 31 * result + additionalVisibleModuleNames.hashCode()
    result = 31 * result + productionOutputPath.hashCode()
    result = 31 * result + testOutputPath.hashCode()
    result = 31 * result + sourceSetNames.hashCode()
    result = 31 * result + isTestModule.hashCode()
    result = 31 * result + externalProjectId.hashCode()
    result = 31 * result + isHmppEnabled.hashCode()
    result = 31 * result + pureKotlinSourceFolders.hashCode()
    result = 31 * result + kind.hashCode()
    result = 31 * result + compilerArguments.hashCode()
    result = 31 * result + compilerSettings.hashCode()
    result = 31 * result + targetPlatform.hashCode()
    result = 31 * result + externalSystemRunTasks.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + flushNeeded.hashCode()
    return result
  }

  override fun hashCodeIgnoringEntitySource(): Int {
    var result = javaClass.hashCode()
    result = 31 * result + moduleId.hashCode()
    result = 31 * result + name.hashCode()
    result = 31 * result + sourceRoots.hashCode()
    result = 31 * result + configFileItems.hashCode()
    result = 31 * result + useProjectSettings.hashCode()
    result = 31 * result + implementedModuleNames.hashCode()
    result = 31 * result + dependsOnModuleNames.hashCode()
    result = 31 * result + additionalVisibleModuleNames.hashCode()
    result = 31 * result + productionOutputPath.hashCode()
    result = 31 * result + testOutputPath.hashCode()
    result = 31 * result + sourceSetNames.hashCode()
    result = 31 * result + isTestModule.hashCode()
    result = 31 * result + externalProjectId.hashCode()
    result = 31 * result + isHmppEnabled.hashCode()
    result = 31 * result + pureKotlinSourceFolders.hashCode()
    result = 31 * result + kind.hashCode()
    result = 31 * result + compilerArguments.hashCode()
    result = 31 * result + compilerSettings.hashCode()
    result = 31 * result + targetPlatform.hashCode()
    result = 31 * result + externalSystemRunTasks.hashCode()
    result = 31 * result + version.hashCode()
    result = 31 * result + flushNeeded.hashCode()
    return result
  }
}
