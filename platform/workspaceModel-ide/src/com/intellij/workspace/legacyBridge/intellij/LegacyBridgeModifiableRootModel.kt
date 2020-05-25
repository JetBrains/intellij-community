package com.intellij.workspace.legacyBridge.intellij

import com.intellij.configurationStore.serializeStateInto
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkTableImpl
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.isEmpty
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.WorkspaceModel
import com.intellij.workspace.ide.getInstance
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibrary
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibraryImpl
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeModifiableBase
import com.intellij.workspace.legacyBridge.roots.LegacyBridgeModifiableContentEntryImpl
import com.intellij.workspace.legacyBridge.typedModel.module.LibraryOrderEntryViaTypedEntity
import com.intellij.workspace.legacyBridge.typedModel.module.OrderEntryViaTypedEntity
import com.intellij.workspace.legacyBridge.typedModel.module.RootModelViaTypedEntityImpl
import com.intellij.workspace.legacyBridge.typedModel.module.SdkOrderEntryViaTypedEntity
import org.jdom.Element
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

class LegacyBridgeModifiableRootModel(
  diff: TypedEntityStorageBuilder,
  override val legacyBridgeModule: LegacyBridgeModule,
  internal val moduleId: ModuleId,
  private val initialStorage: TypedEntityStorage,
  override val accessor: RootConfigurationAccessor
) : LegacyBridgeModifiableBase(diff), ModifiableRootModel, ModificationTracker, LegacyBridgeModuleRootModel {

  override fun getModificationCount(): Long = diff.modificationCount

  private val extensionsDisposable = Disposer.newDisposable()

  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)

  private val extensionsDelegate = lazy {
    RootModelViaTypedEntityImpl.loadExtensions(storage = initialStorage, module = module, writable = true,
                                               parentDisposable = extensionsDisposable)
      .filterNot { compilerModuleExtensionClass.isAssignableFrom(it.javaClass) }
  }
  private val extensions by extensionsDelegate

  private val moduleEntityValue: CachedValue<ModuleEntity?> = CachedValue {
    it.resolve(moduleId)
  }

  internal val moduleEntity: ModuleEntity
    get() = entityStoreOnDiff.cachedValue(moduleEntityValue) ?: error("Unable to resolve module by id '$moduleId'")

  private val moduleLibraryTable = LegacyBridgeModifiableModuleLibraryTable(this,
                                                                            LegacyBridgeModuleRootComponent.getInstance(module).moduleLibraryTable.moduleLibraries)

  private val contentEntriesImplValue: CachedValue<List<LegacyBridgeModifiableContentEntryImpl>> = CachedValue { storage ->
    val moduleEntity = storage.resolve(moduleId) ?: return@CachedValue emptyList<LegacyBridgeModifiableContentEntryImpl>()
    val contentEntries = moduleEntity.contentRoots.toList()

    contentEntries.map {
      LegacyBridgeModifiableContentEntryImpl(
        diff = diff,
        contentEntryUrl = it.url,
        modifiableRootModel = this
      )
    }
  }

  override val storage: TypedEntityStorage
    get() = entityStoreOnDiff.current

  private val contentEntries
    get() = entityStoreOnDiff.cachedValue(contentEntriesImplValue)

  override fun getProject(): Project = legacyBridgeModule.project

  override fun addContentEntry(root: VirtualFile): ContentEntry =
    addContentEntry(root.url)

  override fun addContentEntry(url: String): ContentEntry {
    assertModelIsLive()

    val existingEntry = contentEntries.firstOrNull { it.url == url }
    if (existingEntry != null) {
      return existingEntry
    }

    diff.addContentRootEntity(
        module = moduleEntity,
        excludedUrls = emptyList(),
        excludedPatterns = emptyList(),
        url = virtualFileManager.fromUrl(url),
        source = moduleEntity.entitySource
    )

    // TODO It's N^2 operations since we need to recreate contentEntries every time
    return contentEntries.firstOrNull { it.url == url }
           ?: error("addContentEntry: unable to find content entry after adding: $url to module ${moduleEntity.name}")
  }

  override fun removeContentEntry(entry: ContentEntry) {
    assertModelIsLive()

    val entryImpl = entry as LegacyBridgeModifiableContentEntryImpl
    val contentEntryUrl = entryImpl.contentEntryUrl

    val entity = currentModel.contentEntities.firstOrNull { it.url == contentEntryUrl }
                 ?: error("ContentEntry $entry does not belong to modifiableRootModel of module ${legacyBridgeModule.name}")

    entry.clearSourceFolders()
    diff.removeEntity(entity)

    if (assertChangesApplied && contentEntries.any { it.url == contentEntryUrl.url }) {
      error("removeContentEntry: removed content entry url '$contentEntryUrl' still exists after removing")
    }
  }

  override fun addOrderEntry(orderEntry: OrderEntry) {
    assertModelIsLive()
    when (orderEntry) {
      is LibraryOrderEntryViaTypedEntity -> {
        if (orderEntry.isModuleLevel) {
          moduleLibraryTable.addCopiedLibrary(orderEntry.library as LegacyBridgeLibraryImpl)
        }

        updateDependencies { it + orderEntry.libraryDependencyItem }
      }

      is ModuleOrderEntry -> orderEntry.module?.let { addModuleOrderEntry(it) } ?: error("Module is empty: $orderEntry")
      is ModuleSourceOrderEntry -> updateDependencies { it + ModuleDependencyItem.ModuleSourceDependency }

      is InheritedJdkOrderEntry -> updateDependencies { it + ModuleDependencyItem.InheritedSdkDependency }
      is ModuleJdkOrderEntry -> updateDependencies { it + (orderEntry as SdkOrderEntryViaTypedEntity).sdkDependencyItem }

      else -> error("OrderEntry should not be extended by external systems")
    }
  }

  override fun addLibraryEntry(library: Library): LibraryOrderEntry {
    val libraryId = if (library is LegacyBridgeLibrary) library.libraryId else {
      val libraryName = library.name
      if (libraryName.isNullOrEmpty()) {
        error("Library name is null or empty: $library")
      }

      LibraryId(libraryName, toLibraryTableId(library.table.tableLevel))
    }

    val libraryDependency = ModuleDependencyItem.Exportable.LibraryDependency(
      library = libraryId,
      exported = false,
      scope = ModuleDependencyItem.DependencyScope.COMPILE
    )

    updateDependencies { it + libraryDependency }


    val libraryOrderEntry = (orderEntriesImpl.lastOrNull() as? LibraryOrderEntry
                             ?: error("Unable to find library orderEntry after adding"))
    return libraryOrderEntry
  }

  override fun addInvalidLibrary(name: String, level: String): LibraryOrderEntry {
    val libraryDependency = ModuleDependencyItem.Exportable.LibraryDependency(
      library = LibraryId(name, toLibraryTableId(level)),
      exported = false,
      scope = ModuleDependencyItem.DependencyScope.COMPILE
    )

    updateDependencies { it + libraryDependency }

    return (orderEntriesImpl.lastOrNull() as? LibraryOrderEntry
                             ?: error("Unable to find library orderEntry after adding"))
  }

  override fun addModuleOrderEntry(module: Module): ModuleOrderEntry {
    val moduleDependency = ModuleDependencyItem.Exportable.ModuleDependency(
      module = (module as LegacyBridgeModule).moduleEntityId,
      productionOnTest = false,
      exported = false,
      scope = ModuleDependencyItem.DependencyScope.COMPILE
    )

    updateDependencies { it + moduleDependency }

    return orderEntriesImpl.lastOrNull() as? ModuleOrderEntry
           ?: error("Unable to find module orderEntry after adding")
  }

  override fun addInvalidModuleEntry(name: String): ModuleOrderEntry {
    val moduleDependency = ModuleDependencyItem.Exportable.ModuleDependency(
      module = ModuleId(name),
      productionOnTest = false,
      exported = false,
      scope = ModuleDependencyItem.DependencyScope.COMPILE
    )

    updateDependencies { it + moduleDependency }

    return orderEntriesImpl.lastOrNull() as? ModuleOrderEntry
           ?: error("Unable to find module orderEntry after adding")
  }

  override fun findModuleOrderEntry(module: Module): ModuleOrderEntry? {
    return orderEntries
      .filterIsInstance<ModuleOrderEntry>()
      .firstOrNull { module == it.module }
  }

  override fun findLibraryOrderEntry(library: Library): LibraryOrderEntry? {
    if (library is LegacyBridgeLibrary) {
      val libraryIdToFind = (library as LegacyBridgeLibrary).libraryId
      return orderEntries
        .filterIsInstance<LibraryOrderEntry>()
        .firstOrNull { libraryIdToFind == (it.library as? LegacyBridgeLibrary)?.libraryId }
    }
    else {
      return orderEntries.filterIsInstance<LibraryOrderEntry>().firstOrNull { it.library == library }
    }
  }

  override fun removeOrderEntry(orderEntry: OrderEntry) {
    assertModelIsLive()

    val entryImpl = orderEntry as OrderEntryViaTypedEntity
    val item = entryImpl.item

    if (orderEntriesImpl.none { it.item == item }) {
      error("OrderEntry $item does not belong to modifiableRootModel of module ${legacyBridgeModule.name}")
    }

    if (orderEntry is LibraryOrderEntryViaTypedEntity && orderEntry.isModuleLevel) {
      moduleLibraryTable.removeLibrary(orderEntry.library as LegacyBridgeLibrary)
    } else {
      updateDependencies { dependencies -> dependencies.filter { it != item } }
    }

    if (assertChangesApplied && orderEntriesImpl.any { it.item == item })
      error("removeOrderEntry: removed order entry $item still exists after removing")
  }

  override fun rearrangeOrderEntries(newOrder: Array<out OrderEntry>) {
    val newEntities = newOrder.map { it as OrderEntryViaTypedEntity }.map { it.item }
    if (newEntities.toSet() != moduleEntity.dependencies.toSet()) {
      error("Expected the same entities as existing order entries, but in a different order")
    }

    updateDependencies { newEntities }

    if (assertChangesApplied) {
      if (orderEntriesImpl.map { it.item } != newEntities) {
        error("rearrangeOrderEntries: wrong order after rearranging entries")
      }
    }
  }

  override fun clear() {
    for (library in moduleLibraryTable.libraries) {
      moduleLibraryTable.removeLibrary(library)
    }

    val currentSdk = sdk
    updateDependencies { dependencies ->
      val jdkItem = currentSdk?.let { ModuleDependencyItem.SdkDependency(it.name, it.sdkType.name)}
      listOfNotNull(jdkItem, ModuleDependencyItem.ModuleSourceDependency)
    }

    for (contentRoot in moduleEntity.contentRoots) {
      diff.removeEntity(contentRoot)
    }

    for (sourceRoot in moduleEntity.sourceRoots) {
      diff.removeEntity(sourceRoot)
    }
  }

  fun collectChangesAndDispose(): TypedEntityStorageBuilder? {
    assertModelIsLive()
    Disposer.dispose(moduleLibraryTable)
    if (!isChanged) return null

    if (extensionsDelegate.isInitialized() && extensions.any { it.isChanged }) {
      val element = Element("component")

      for (extension in extensions) {
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
          customImlDataEntity == null && !element.isEmpty() -> diff.addModuleCustomImlDataEntity(
            module = moduleEntity,
            rootManagerTagCustomData = elementAsString,
            customModuleOptions = emptyMap(),
            source = moduleEntity.entitySource
          )

          customImlDataEntity == null && element.isEmpty() -> Unit

          customImlDataEntity != null && customImlDataEntity.customModuleOptions.isEmpty() && element.isEmpty() ->
            diff.removeEntity(customImlDataEntity)

          customImlDataEntity != null && !element.isEmpty() -> diff.modifyEntity(ModifiableModuleCustomImlDataEntity::class.java,
            customImlDataEntity) {
            rootManagerTagCustomData = elementAsString
          }

          else -> error("Should not be reached")
        }
      }
    }

    disposeWithoutLibraries()
    return diff
  }

  override fun commit() {
    val diff = collectChangesAndDispose() ?: return
    val moduleDiff = module.diff
    if (moduleDiff != null) {
      moduleDiff.addDiff(diff)
    } else {
      WorkspaceModel.getInstance(project).updateProjectModel {
        it.addDiff(diff)
      }
    }
  }

  override fun dispose() {
    disposeWithoutLibraries()
    Disposer.dispose(moduleLibraryTable)
  }

  private fun disposeWithoutLibraries() {
    if (!modelIsCommittedOrDisposed) {
      Disposer.dispose(extensionsDisposable)
    }

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
    } else {
      val jdkTable = ProjectJdkTable.getInstance()
      if (jdkTable.findJdk (jdk.name, jdk.sdkType.name) == null) {
        if (ApplicationManager.getApplication().isUnitTestMode) {
          // TODO Fix all tests and remove this
          (jdkTable as ProjectJdkTableImpl).addTestJdk(jdk, project)
        } else {
          error("setSdk: sdk '${jdk.name}' type '${jdk.sdkType.name}' is not registered in ProjectJdkTable")
        }
      }

      setInvalidSdk(jdk.name, jdk.sdkType.name)
    }
  }

  override fun setInvalidSdk(sdkName: String, sdkType: String) {
    setSdkItem(ModuleDependencyItem.SdkDependency(sdkName, sdkType))

    if (assertChangesApplied && getSdkName() != sdkName) {
      error("setInvalidSdk: expected sdkName '$sdkName' but got '${getSdkName()}' after doing a change")
    }
  }

  private val orderEntriesImpl
    get() = orderEntries.map { it as OrderEntryViaTypedEntity }

  override fun inheritSdk() {
    if (isSdkInherited) return

    setSdkItem(ModuleDependencyItem.InheritedSdkDependency)

    if (assertChangesApplied && !isSdkInherited) {
      error("inheritSdk: Sdk is still not inherited after inheritSdk()")
    }
  }

  // TODO compare by actual values
  override fun isChanged(): Boolean {
    if (!diff.isEmpty()) return true

    if (extensionsDelegate.isInitialized() && extensions.any { it.isChanged }) return true

    return false
  }

  override fun isWritable(): Boolean = true

  override fun <T : OrderEntry?> replaceEntryOfType(entryClass: Class<T>, entry: T) =
    throw NotImplementedError("Not implemented since it was used only by project model implementation")

  override fun getSdkName(): String? = orderEntries.filterIsInstance<JdkOrderEntry>().firstOrNull()?.jdkName

  // TODO
  override fun isDisposed(): Boolean = modelIsCommittedOrDisposed

  private fun setSdkItem(item: ModuleDependencyItem?) = updateDependencies { dependencies ->
    listOfNotNull(item) +
    dependencies
      .filter { it !is ModuleDependencyItem.InheritedSdkDependency }
      .filter { it !is ModuleDependencyItem.SdkDependency }
  }

  internal fun updateDependencies(updater: (List<ModuleDependencyItem>) -> List<ModuleDependencyItem>) {
    val newDependencies = updater(moduleEntity.dependencies)
    if (newDependencies == moduleEntity.dependencies) return

    diff.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) {
      dependencies = newDependencies
    }
  }

  private val modelValue = CachedValue { storage ->
    RootModelViaTypedEntityImpl(
      moduleEntityId = moduleId,
      storage = storage,
      moduleLibraryTable = moduleLibraryTable,
      itemUpdater = { index, transformer -> updateDependencies { dependencies ->
          val mutableList = dependencies.toMutableList()

          val old = mutableList[index]
          val new = transformer(old)
          mutableList[index] = new

          mutableList.toList()
        }
      },
      rootModel = this,
      updater = { transformer -> transformer(diff) }
    )
  }

  internal val currentModel
    get() = entityStoreOnDiff.cachedValue(modelValue)

  private val compilerModuleExtension by lazy {
    LegacyBridgeCompilerModuleExtension(legacyBridgeModule, entityStore = entityStoreOnDiff, diff = diff)
  }
  private val compilerModuleExtensionClass = CompilerModuleExtension::class.java

  override fun getExcludeRoots(): Array<VirtualFile> = currentModel.excludeRoots
  override fun orderEntries(): OrderEnumerator = currentModel.orderEntries()

  override fun <T : Any?> getModuleExtension(klass: Class<T>): T? {
    if (compilerModuleExtensionClass.isAssignableFrom(klass)) {
      @Suppress("UNCHECKED_CAST")
      return compilerModuleExtension as T
    }

    return extensions.filterIsInstance(klass).firstOrNull()
  }

  override fun getDependencyModuleNames(): Array<String> = currentModel.dependencyModuleNames
  override fun getModule(): LegacyBridgeModule = legacyBridgeModule
  override fun isSdkInherited(): Boolean = currentModel.isSdkInherited
  override fun getOrderEntries(): Array<OrderEntry> = currentModel.orderEntries
  override fun getSourceRootUrls(): Array<String> = currentModel.sourceRootUrls
  override fun getSourceRootUrls(includingTests: Boolean): Array<String> = currentModel.getSourceRootUrls(includingTests)
  override fun getContentEntries(): Array<ContentEntry> = contentEntries.toTypedArray()
  override fun getExcludeRootUrls(): Array<String> = currentModel.excludeRootUrls
  override fun <R : Any?> processOrder(policy: RootPolicy<R>, initialValue: R): R = currentModel.processOrder(policy, initialValue)
  override fun getSdk(): Sdk? = currentModel.sdk
  override fun getSourceRoots(): Array<VirtualFile> = currentModel.sourceRoots
  override fun getSourceRoots(includingTests: Boolean): Array<VirtualFile> = currentModel.getSourceRoots(includingTests)
  override fun getSourceRoots(rootType: JpsModuleSourceRootType<*>): MutableList<VirtualFile> = currentModel.getSourceRoots(rootType)
  override fun getSourceRoots(rootTypes: MutableSet<out JpsModuleSourceRootType<*>>): MutableList<VirtualFile> = currentModel.getSourceRoots(rootTypes)
  override fun getContentRoots(): Array<VirtualFile> = currentModel.contentRoots
  override fun getContentRootUrls(): Array<String> = currentModel.contentRootUrls
  override fun getModuleDependencies(): Array<Module> = currentModel.moduleDependencies
  override fun getModuleDependencies(includeTests: Boolean): Array<Module> = currentModel.getModuleDependencies(includeTests)
}

fun toLibraryTableId(level: String) = when (level) {
  JpsLibraryTableSerializer.MODULE_LEVEL -> error("this method isn't supposed to be used for module-level libraries")
  JpsLibraryTableSerializer.PROJECT_LEVEL -> LibraryTableId.ProjectLibraryTableId
  else -> LibraryTableId.GlobalLibraryTableId(level)
}