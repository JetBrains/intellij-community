// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.configurationStore.serializeStateInto
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.ModuleOrderEnumerator
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.openapi.roots.impl.RootModelBase.CollectDependentModules
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.CustomModuleEntitySource
import com.intellij.platform.workspace.jps.JpsFileDependentEntitySource
import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.entities.DependencyScope as EntitiesDependencyScope
import com.intellij.platform.workspace.jps.serialization.impl.LibraryNameGenerator
import com.intellij.platform.workspace.storage.CachedValue
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.ArrayUtilRt
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeModifiableBase
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleExtensionBridge
import org.jdom.Element
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import java.util.concurrent.ConcurrentHashMap

internal class ModifiableRootModelBridgeImpl(
  diff: MutableEntityStorage,
  override val moduleBridge: ModuleBridge,
  override val accessor: RootConfigurationAccessor,
  cacheStorageResult: Boolean = true
) : LegacyBridgeModifiableBase(diff, cacheStorageResult), ModifiableRootModelBridge, ModuleRootModelBridge {

  /*
    We save the module entity for the following case:
    - Modifiable model created
    - module disposed
    - modifiable model used

    This case can appear, for example, during maven import

    moduleEntity would be removed from this diff after module disposing
  */
  private var savedModuleEntity: ModuleEntity

  init {
    savedModuleEntity = getModuleEntity(entityStorageOnDiff.current, module)
                        ?: error("Cannot find module entity for ${module.moduleEntityId}. Bridge: '$moduleBridge'. Store: $diff")
  }

  private fun getModuleEntity(current: EntityStorage, myModuleBridge: ModuleBridge): ModuleEntity? {
    // Try to get entity by module id
    // In some cases this won't work. These cases can happen during maven or gradle import where we provide a general builder.
    //   The case: we rename the module. Since the changes not yet committed, the module will remain with the old symbolicId. After that
    //   we try to get modifiableRootModel. In general case it would work fine because the builder will be based on main store, but
    //   in case of gradle/maven import we take the builder that was used for renaming. So, the old name cannot be found in the new store.
    return current.resolve(myModuleBridge.moduleEntityId) ?: myModuleBridge.findModuleEntity(current)
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun getModificationCount(): Long = (diff as MutableEntityStorageInstrumentation).modificationCount

  private val virtualFileManager: VirtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()

  private val extensionsDelegate = lazy {
    RootModelBridgeImpl.loadExtensions(storage = entityStorageOnDiff, module = module, diff = diff, writable = true)
  }
  private val extensions by extensionsDelegate

  private val sourceRootPropertiesMap = ConcurrentHashMap<VirtualFileUrl, JpsModuleSourceRoot>()

  internal val moduleEntity: ModuleEntity
    get() {
      val actualModuleEntity = getModuleEntity(entityStorageOnDiff.current, module) ?: return savedModuleEntity
      savedModuleEntity = actualModuleEntity
      return actualModuleEntity
    }

  // It's needed to track changed dependency to create new instance of Library if e.g dependency scope was changed
  private val changedLibraryDependency = mutableSetOf<LibraryId>()
  private val moduleLibraryTable = ModifiableModuleLibraryTableBridge(this)

  /**
   * Contains instances of OrderEntries edited via [ModifiableRootModel] interfaces; we need to keep references to them to update their indices;
   * it should be used for modifications only, in order to read actual state one need to use [orderEntriesArray].
   */
  private val mutableOrderEntries: ArrayList<OrderEntryBridge> by lazy {
    ArrayList<OrderEntryBridge>().also { addOrderEntries(moduleEntity.dependencies, it) }
  }

  /**
   * Provides cached value for [mutableOrderEntries] converted to an array to avoid creating array each time [getOrderEntries] is called;
   * also it updates instances in [mutableOrderEntries] when underlying entities are changed via [WorkspaceModel] interface (e.g. when a
   * library referenced from [LibraryOrderEntry] is renamed).
   */
  private val orderEntriesArrayValue: CachedValue<Array<OrderEntry>> = CachedValue { storage ->
    val dependencies = module.findModuleEntity(storage)?.dependencies ?: return@CachedValue emptyArray()
    if (mutableOrderEntries.size == dependencies.size) {
      //keep old instances of OrderEntries if possible (i.e. if only some properties of order entries were changes via WorkspaceModel)
      for (i in mutableOrderEntries.indices) {
        if (dependencies[i] != mutableOrderEntries[i].item && dependencies[i].javaClass == mutableOrderEntries[i].item.javaClass) {
          mutableOrderEntries[i].item = dependencies[i]
        }
      }
    }
    else {
      mutableOrderEntries.clear()
      addOrderEntries(dependencies, mutableOrderEntries)
    }
    mutableOrderEntries.toTypedArray()
  }
  private val orderEntriesArray
    get() = entityStorageOnDiff.cachedValue(orderEntriesArrayValue)

  private fun addOrderEntries(dependencies: List<ModuleDependencyItem>, target: MutableList<OrderEntryBridge>) =
    dependencies.mapIndexedTo(target) { index, item ->
      RootModelBridgeImpl.toOrderEntry(item, index, this, this::updateDependencyItem)
    }

  private val contentEntriesImplValue: CachedValue<List<ModifiableContentEntryBridge>> = CachedValue { storage ->
    val moduleEntity = module.findModuleEntity(storage) ?: return@CachedValue emptyList<ModifiableContentEntryBridge>()
    val contentEntries = moduleEntity.contentRoots.sortedBy { it.url.url }.toList()

    contentEntries.map {
      ModifiableContentEntryBridge(
        diff = diff,
        contentEntryUrl = it.url,
        modifiableRootModel = this
      )
    }
  }

  private fun updateDependencyItem(index: Int, transformer: (ModuleDependencyItem) -> ModuleDependencyItem) {
    val oldItem = moduleEntity.dependencies[index]
    val newItem = transformer(oldItem)
    if (oldItem == newItem) return

    diff.modifyModuleEntity(moduleEntity) {
      val copy = dependencies.toMutableList()
      copy[index] = newItem
      dependencies = copy
    }

    if (newItem is LibraryDependency && newItem.library.tableId is LibraryTableId.ModuleLibraryTableId) {
      changedLibraryDependency.add(newItem.library)
    }
  }

  override val storage: EntityStorage
    get() = entityStorageOnDiff.current

  override fun getOrCreateJpsRootProperties(sourceRootUrl: VirtualFileUrl, creator: () -> JpsModuleSourceRoot): JpsModuleSourceRoot {
    return sourceRootPropertiesMap.computeIfAbsent(sourceRootUrl) { creator() }
  }

  override fun removeCachedJpsRootProperties(sourceRootUrl: VirtualFileUrl) {
    sourceRootPropertiesMap.remove(sourceRootUrl)
  }

  private val contentEntries
    get() = entityStorageOnDiff.cachedValue(contentEntriesImplValue)

  override fun getProject(): Project = moduleBridge.project

  override fun addContentEntry(root: VirtualFile): ContentEntry {
    return addContentEntry(root.url)
  }

  override fun addContentEntry(root: VirtualFile, externalSource: ProjectModelExternalSource): ContentEntry {
    return addContentEntry(root.url, externalSource)
  }

  override fun addContentEntry(url: String): ContentEntry {
    return addContentEntry(url, false)
  }

  override fun addContentEntry(url: String, externalSource: ProjectModelExternalSource): ContentEntry {
    return addContentEntry(url, true)
  }

  override fun addContentEntry(url: String, useSourceOfModule: Boolean): ContentEntry {
    assertModelIsLive()

    LOG.debugWithTrace { "Add content entry for url: $url, useSourceOfModule: $useSourceOfModule" }

    val finalSource = if (useSourceOfModule) moduleEntity.entitySource
    else getInternalFileSource(moduleEntity.entitySource) ?: moduleEntity.entitySource
    return addEntityAndContentEntry(url, finalSource)
  }

  private fun addEntityAndContentEntry(url: String, entitySource: EntitySource): ContentEntry {
    val virtualFileUrl = virtualFileManager.getOrCreateFromUrl(url)
    val existingEntry = contentEntries.firstOrNull { it.contentEntryUrl == virtualFileUrl }
    if (existingEntry != null) {
      return existingEntry
    }

    diff.modifyModuleEntity(moduleEntity) {
      this.contentRoots += ContentRootEntity(url = virtualFileUrl,
                                             excludedPatterns = emptyList<@NlsSafe String>(),
                                             entitySource = entitySource)
    }

    // TODO It's N^2 operations since we need to recreate contentEntries every time
    return contentEntries.firstOrNull { it.contentEntryUrl == virtualFileUrl }
           ?: error("addContentEntry: unable to find content entry after adding: $url to module ${moduleEntity.name}")
  }

  override fun removeContentEntry(entry: ContentEntry) {
    assertModelIsLive()

    LOG.debugWithTrace { "Remove content entry for url: ${entry.url}" }

    val entryImpl = entry as ModifiableContentEntryBridge
    val contentEntryUrl = entryImpl.contentEntryUrl

    val entity = currentModel.contentEntities.firstOrNull { it.url == contentEntryUrl }
                 ?: error("ContentEntry $entry does not belong to modifiableRootModel of module ${moduleBridge.name}")

    entry.clearSourceFolders()
    diff.removeEntity(entity)

    if (assertChangesApplied && contentEntries.any { it.url == contentEntryUrl.url }) {
      error("removeContentEntry: removed content entry url '$contentEntryUrl' still exists after removing")
    }
  }

  override fun addOrderEntry(orderEntry: OrderEntry) {
    assertModelIsLive()
    when (orderEntry) {
      is LibraryOrderEntryBridge -> {
        if (orderEntry.isModuleLevel) {
          moduleLibraryTable.addLibraryCopy(orderEntry.library as LibraryBridgeImpl, orderEntry.isExported,
                                            orderEntry.libraryDependencyItem.scope)
        }
        else {
          appendDependency(orderEntry.libraryDependencyItem)
        }
      }

      is ModuleOrderEntry -> orderEntry.module?.let { addModuleOrderEntry(it) } ?: error("Module is empty: $orderEntry")
      is ModuleSourceOrderEntry -> appendDependency(ModuleSourceDependency)

      is InheritedJdkOrderEntry -> appendDependency(InheritedSdkDependency)
      is ModuleJdkOrderEntry -> appendDependency((orderEntry as SdkOrderEntryBridge).sdkDependencyItem)

      else -> error("OrderEntry should not be extended by external systems")
    }
  }

  override fun addLibraryEntry(library: Library): LibraryOrderEntry {
    appendDependency(LibraryDependency(
      library = library.libraryId,
      exported = false,
      scope = EntitiesDependencyScope.COMPILE
    ))
    return (mutableOrderEntries.lastOrNull() as? LibraryOrderEntry ?: error("Unable to find library orderEntry after adding"))
  }

  private val Library.libraryId: LibraryId
    get() {
      val libraryId = if (this is LibraryBridge) libraryId
      else {
        val libraryName = name
        if (libraryName.isNullOrEmpty()) {
          error("Library name is null or empty: $this")
        }

        LibraryId(libraryName, LibraryNameGenerator.getLibraryTableId(table.tableLevel))
      }
      return libraryId
    }

  override fun addLibraryEntries(libraries: List<Library>, scope: DependencyScope, exported: Boolean) {
    val dependencyScope = scope.toEntityDependencyScope()
    appendDependencies(libraries.map {
      LibraryDependency(it.libraryId, exported, dependencyScope)
    })
  }

  override fun addInvalidLibrary(name: String, level: String): LibraryOrderEntry {
    val libraryDependency = LibraryDependency(
      library = LibraryId(name, LibraryNameGenerator.getLibraryTableId(level)),
      exported = false,
      scope = EntitiesDependencyScope.COMPILE
    )

    appendDependency(libraryDependency)

    return (mutableOrderEntries.lastOrNull() as? LibraryOrderEntry ?: error("Unable to find library orderEntry after adding"))
  }

  override fun addModuleOrderEntry(module: Module): ModuleOrderEntry {
    val moduleDependency = ModuleDependency(
      module = (module as ModuleBridge).moduleEntityId,
      productionOnTest = false,
      exported = false,
      scope = EntitiesDependencyScope.COMPILE
    )

    appendDependency(moduleDependency)

    return mutableOrderEntries.lastOrNull() as? ModuleOrderEntry ?: error("Unable to find module orderEntry after adding")
  }

  override fun addModuleEntries(modules: MutableList<Module>, scope: DependencyScope, exported: Boolean) {
    val dependencyScope = scope.toEntityDependencyScope()
    appendDependencies(modules.map {
      ModuleDependency((it as ModuleBridge).moduleEntityId, exported, dependencyScope, productionOnTest = false)
    })
  }

  override fun addInvalidModuleEntry(name: String): ModuleOrderEntry {
    val moduleDependency = ModuleDependency(
      module = ModuleId(name),
      productionOnTest = false,
      exported = false,
      scope = EntitiesDependencyScope.COMPILE
    )

    appendDependency(moduleDependency)

    return mutableOrderEntries.lastOrNull() as? ModuleOrderEntry ?: error("Unable to find module orderEntry after adding")
  }

  internal fun appendDependency(dependency: ModuleDependencyItem) {
    mutableOrderEntries.add(RootModelBridgeImpl.toOrderEntry(dependency, mutableOrderEntries.size, this, this::updateDependencyItem))
    entityStorageOnDiff.clearCachedValue(orderEntriesArrayValue)
    diff.modifyModuleEntity(moduleEntity) {
      dependencies.add(dependency)
    }
  }

  private fun appendDependencies(dependencies: List<ModuleDependencyItem>) {
    for (dependency in dependencies) {
      mutableOrderEntries.add(RootModelBridgeImpl.toOrderEntry(dependency, mutableOrderEntries.size, this, this::updateDependencyItem))
    }
    entityStorageOnDiff.clearCachedValue(orderEntriesArrayValue)
    diff.modifyModuleEntity(moduleEntity) {
      this.dependencies.addAll(dependencies)
    }
  }

  private fun insertDependency(dependency: ModuleDependencyItem, position: Int): OrderEntryBridge {
    val last = position == mutableOrderEntries.size
    val newEntry = RootModelBridgeImpl.toOrderEntry(dependency, position, this, this::updateDependencyItem)
    if (last) {
      mutableOrderEntries.add(newEntry)
    }
    else {
      mutableOrderEntries.add(position, newEntry)
      for (i in position + 1 until mutableOrderEntries.size) {
        mutableOrderEntries[i].updateIndex(i)
      }
    }
    entityStorageOnDiff.clearCachedValue(orderEntriesArrayValue)
    diff.modifyModuleEntity(moduleEntity) {
      if (last) {
        dependencies.add(dependency)
      }
      else {
        val result = mutableListOf<ModuleDependencyItem>()
        result.addAll(dependencies.subList(0, position))
        result.add(dependency)
        result.addAll(dependencies.subList(position, dependencies.size))
        dependencies = result
      }
    }
    return newEntry
  }

  internal fun removeDependencies(filter: (Int, ModuleDependencyItem) -> Boolean) {
    val newDependencies = ArrayList<ModuleDependencyItem>()
    val newOrderEntries = ArrayList<OrderEntryBridge>()
    val oldDependencies = moduleEntity.dependencies
    for (i in oldDependencies.indices) {
      if (!filter(i, oldDependencies[i])) {
        newDependencies.add(oldDependencies[i])
        val entryBridge = mutableOrderEntries[i]
        entryBridge.updateIndex(newOrderEntries.size)
        newOrderEntries.add(entryBridge)
      }
    }
    mutableOrderEntries.clear()
    mutableOrderEntries.addAll(newOrderEntries)
    entityStorageOnDiff.clearCachedValue(orderEntriesArrayValue)
    diff.modifyModuleEntity(moduleEntity) {
      dependencies = newDependencies
    }
  }

  override fun findModuleOrderEntry(module: Module): ModuleOrderEntry? {
    return orderEntries.filterIsInstance<ModuleOrderEntry>().firstOrNull { module == it.module }
  }

  override fun findLibraryOrderEntry(library: Library): LibraryOrderEntry? {
    if (library is LibraryBridge) {
      val libraryIdToFind = library.libraryId
      return orderEntries
        .filterIsInstance<LibraryOrderEntry>()
        .firstOrNull { libraryIdToFind == (it.library as? LibraryBridge)?.libraryId }
    }
    else {
      return orderEntries.filterIsInstance<LibraryOrderEntry>().firstOrNull { it.library == library }
    }
  }

  override fun removeOrderEntry(orderEntry: OrderEntry) {
    assertModelIsLive()

    val entryImpl = orderEntry as OrderEntryBridge
    val item = entryImpl.item

    if (mutableOrderEntries.none { it.item == item }) {
      LOG.error("OrderEntry $item does not belong to modifiableRootModel of module ${moduleBridge.name}")
      return
    }

    if (orderEntry is LibraryOrderEntryBridge && orderEntry.isModuleLevel) {
      moduleLibraryTable.removeLibrary(orderEntry.library as LibraryBridge)
    }
    else {
      val itemIndex = entryImpl.currentIndex
      removeDependencies { index, _ -> index == itemIndex }
    }
  }

  override fun rearrangeOrderEntries(newOrder: Array<out OrderEntry>) {
    val newOrderEntries = newOrder.mapTo(ArrayList()) { it as OrderEntryBridge }
    val newEntities = newOrderEntries.map { it.item }
    if (newEntities.toSet() != moduleEntity.dependencies.toSet()) {
      error("Expected the same entities as existing order entries, but in a different order")
    }

    mutableOrderEntries.clear()
    mutableOrderEntries.addAll(newOrderEntries)
    for (i in mutableOrderEntries.indices) {
      mutableOrderEntries[i].updateIndex(i)
    }
    entityStorageOnDiff.clearCachedValue(orderEntriesArrayValue)
    diff.modifyModuleEntity(moduleEntity) {
      dependencies = newEntities.toMutableList()
    }
  }

  override fun clear() {
    for (library in moduleLibraryTable.libraries) {
      moduleLibraryTable.removeLibrary(library)
    }

    val currentSdk = sdk
    val jdkItem = currentSdk?.let { SdkDependency(SdkId(it.name, it.sdkType.name)) }
    if (moduleEntity.dependencies != listOfNotNull(jdkItem, ModuleSourceDependency)) {
      removeDependencies { _, _ -> true }
      if (jdkItem != null) {
        appendDependency(jdkItem)
      }
      appendDependency(ModuleSourceDependency)
    }

    for (contentRoot in moduleEntity.contentRoots) {
      diff.removeEntity(contentRoot)
    }
  }

  fun collectChangesAndDispose(): MutableEntityStorage? {
    assertModelIsLive()
    Disposer.dispose(moduleLibraryTable)
    if (!isChanged) {
      moduleLibraryTable.restoreLibraryMappingsAndDisposeCopies()
      disposeWithoutLibraries()
      return null
    }

    if (extensionsDelegate.isInitialized() && extensions.any { it.isChanged }) {
      val element = Element("component")

      for (extension in extensions) {
        if (extension is ModuleExtensionBridge) continue

        extension.commit()

        if (extension is PersistentStateComponent<*>) {
          serializeStateInto(extension, element)
        }
        else {
          @Suppress("DEPRECATION")
          extension.writeExternal(element)
        }
      }

      val elementAsString = JDOMUtil.writeElement(element)
      val customImlDataEntity = moduleEntity.customImlData

      if (customImlDataEntity?.rootManagerTagCustomData != elementAsString) {
        when {
          customImlDataEntity == null && !JDOMUtil.isEmpty(element) -> {
            diff.modifyModuleEntity(moduleEntity) {
              this.customImlData = ModuleCustomImlDataEntity(HashMap(), moduleEntity.entitySource) {
                rootManagerTagCustomData = elementAsString
              }
            }
          }

          customImlDataEntity == null && JDOMUtil.isEmpty(element) -> Unit

          customImlDataEntity != null && customImlDataEntity.customModuleOptions.isEmpty() && JDOMUtil.isEmpty(element) ->
            diff.removeEntity(customImlDataEntity)

          customImlDataEntity != null && customImlDataEntity.customModuleOptions.isNotEmpty() && JDOMUtil.isEmpty(element) ->
            diff.modifyModuleCustomImlDataEntity(customImlDataEntity) {
              rootManagerTagCustomData = null
            }

          customImlDataEntity != null && !JDOMUtil.isEmpty(element) -> diff.modifyModuleCustomImlDataEntity(customImlDataEntity) {
            rootManagerTagCustomData = elementAsString
          }

          else -> error("Should not be reached")
        }
      }
    }

    if (!sourceRootPropertiesMap.isEmpty()) {
      for (sourceRoot in moduleEntity.sourceRoots) {
        val actualSourceRootData = sourceRootPropertiesMap[sourceRoot.url] ?: continue
        SourceRootPropertiesHelper.applyChanges(diff, sourceRoot, actualSourceRootData)
      }
    }

    moduleLibraryTable.restoreMappingsForUnchangedLibraries(changedLibraryDependency)
    disposeWithoutLibraries()
    return diff
  }

  private fun areSourceRootPropertiesChanged(): Boolean {
    if (sourceRootPropertiesMap.isEmpty()) return false
    return moduleEntity.sourceRoots.any { sourceRoot ->
      val actualSourceRootData = sourceRootPropertiesMap[sourceRoot.url]
      actualSourceRootData != null && !SourceRootPropertiesHelper.hasEqualProperties(sourceRoot, actualSourceRootData)
    }
  }

  override fun commit() {
    val diff = collectChangesAndDispose() ?: return
    val moduleDiff = module.diff
    if (moduleDiff != null) {
      moduleDiff.applyChangesFrom(diff)
      postCommit()
    }
    else {
      WorkspaceModel.getInstance(project).updateProjectModel("Root model commit") {
        it.applyChangesFrom(diff)
      }
      postCommit()
    }
  }

  override fun prepareForCommit() {
    collectChangesAndDispose()
  }

  override fun postCommit() {
    moduleLibraryTable.disposeOriginalLibrariesAndUpdateCopies()
  }

  override fun dispose() {
    disposeWithoutLibraries()
    moduleLibraryTable.restoreLibraryMappingsAndDisposeCopies()
    Disposer.dispose(moduleLibraryTable)
  }

  private fun disposeWithoutLibraries() {
    // No assertions here since it is ok to call dispose twice or more
    modelIsCommittedOrDisposed = true
  }

  override fun getModuleLibraryTable(): LibraryTable = moduleLibraryTable

  override fun setSdk(jdk: Sdk?) {
    if (jdk == null) {
      setSdkItem(null)

      if (assertChangesApplied && sdkName != null) {
        error("setSdk: expected sdkName is null, but got: $sdkName")
      }
    }
    else {
      if (ModifiableRootModelBridge.findSdk(jdk.name, jdk.sdkType.name) == null) {
        error("setSdk: sdk '${jdk.name}' type '${jdk.sdkType.name}' is not registered in ProjectJdkTable")
      }
      setInvalidSdk(jdk.name, jdk.sdkType.name)
    }
  }

  override fun setInvalidSdk(sdkName: String, sdkType: String) {
    setSdkItem(SdkDependency(SdkId(sdkName, sdkType)))

    if (assertChangesApplied && getSdkName() != sdkName) {
      error("setInvalidSdk: expected sdkName '$sdkName' but got '${getSdkName()}' after doing a change")
    }
  }

  override fun inheritSdk() {
    if (isSdkInherited) return

    setSdkItem(InheritedSdkDependency)

    if (assertChangesApplied && !isSdkInherited) {
      error("inheritSdk: Sdk is still not inherited after inheritSdk()")
    }
  }

  // TODO compare by actual values
  @OptIn(EntityStorageInstrumentationApi::class)
  override fun isChanged(): Boolean {
    if ((diff as MutableEntityStorageInstrumentation).hasChanges()) return true

    if (extensionsDelegate.isInitialized() && extensions.any { it.isChanged }) return true

    if (areSourceRootPropertiesChanged()) return true

    return false
  }

  override fun isWritable(): Boolean = true

  override fun getSdkName(): String? = orderEntries.filterIsInstance<JdkOrderEntry>().firstOrNull()?.jdkName

  // TODO
  override fun isDisposed(): Boolean = modelIsCommittedOrDisposed

  private fun setSdkItem(item: ModuleDependencyItem?) {
    removeDependencies { _, it -> it is InheritedSdkDependency || it is SdkDependency }
    if (item != null) {
      insertDependency(item, 0)
    }
  }

  private val modelValue = CachedValue { storage ->
    RootModelBridgeImpl(
      moduleEntity = getModuleEntity(storage, moduleBridge),
      storage = entityStorageOnDiff,
      itemUpdater = null,
      rootModel = this,
      updater = { transformer -> transformer(diff) }
    )
  }

  internal val currentModel
    get() = entityStorageOnDiff.cachedValue(modelValue)

  override fun getExcludeRoots(): Array<VirtualFile> = currentModel.excludeRoots

  override fun orderEntries(): OrderEnumerator = ModuleOrderEnumerator(this, null)

  override fun <T : Any?> getModuleExtension(klass: Class<T>): T? {
    return extensions.filterIsInstance(klass).firstOrNull()
  }

  override fun getDependencyModuleNames(): Array<String> {
    val result = orderEntries().withoutSdk().withoutLibraries().withoutModuleSourceEntries().process(CollectDependentModules(), ArrayList())
    return ArrayUtilRt.toStringArray(result)
  }

  override fun getModule(): ModuleBridge = moduleBridge
  override fun isSdkInherited(): Boolean = orderEntriesArray.any { it is InheritedJdkOrderEntry }
  override fun getOrderEntries(): Array<OrderEntry> = orderEntriesArray
  override fun getSourceRootUrls(): Array<String> = currentModel.sourceRootUrls
  override fun getSourceRootUrls(includingTests: Boolean): Array<String> = currentModel.getSourceRootUrls(includingTests)
  override fun getContentEntries(): Array<ContentEntry> = contentEntries.toTypedArray()
  override fun getExcludeRootUrls(): Array<String> = currentModel.excludeRootUrls
  override fun <R : Any?> processOrder(policy: RootPolicy<R>, initialValue: R): R {
    var result = initialValue
    for (orderEntry in orderEntries) {
      result = orderEntry.accept(policy, result)
    }
    return result
  }

  override fun getSdk(): Sdk? = (orderEntriesArray.find { it is JdkOrderEntry } as JdkOrderEntry?)?.jdk
  override fun getSourceRoots(): Array<VirtualFile> = currentModel.sourceRoots
  override fun getSourceRoots(includingTests: Boolean): Array<VirtualFile> = currentModel.getSourceRoots(includingTests)
  override fun getSourceRoots(rootType: JpsModuleSourceRootType<*>): MutableList<VirtualFile> = currentModel.getSourceRoots(rootType)
  override fun getSourceRoots(rootTypes: MutableSet<out JpsModuleSourceRootType<*>>): MutableList<VirtualFile> = currentModel.getSourceRoots(
    rootTypes)

  override fun getContentRoots(): Array<VirtualFile> = currentModel.contentRoots
  override fun getContentRootUrls(): Array<String> = currentModel.contentRootUrls
  override fun getModuleDependencies(): Array<Module> = getModuleDependencies(true)

  override fun getModuleDependencies(includeTests: Boolean): Array<Module> {
    var result: MutableList<Module>? = null
    for (entry in orderEntriesArray) {
      if (entry is ModuleOrderEntry) {
        val scope = entry.scope
        if (includeTests || scope.isForProductionCompile || scope.isForProductionRuntime) {
          val module = entry.module
          if (module != null) {
            if (result == null) {
              result = ArrayList()
            }
            result.add(module)
          }
        }
      }
    }
    return if (result.isNullOrEmpty()) Module.EMPTY_ARRAY else result.toTypedArray()
  }

  companion object {
    private val LOG = logger<ModifiableRootModelBridgeImpl>()
  }
}

internal fun getInternalFileSource(source: EntitySource) = when (source) {
  is JpsFileDependentEntitySource -> source.originalSource
  is CustomModuleEntitySource -> source.internalSource
  is JpsFileEntitySource -> source
  else -> null
}

/**
 * Print a debug message and add a stack trace if trace logging is enabled
 */
private fun Logger.debugWithTrace(msg: () -> String) {
  val e = if (this.isTraceEnabled) RuntimeException("Stack trace of the log entry:") else null
  this.debug(e, msg)
}
