package com.intellij.workspace.legacyBridge.intellij

import com.intellij.configurationStore.serializeStateInto
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.ProjectJdkTableImpl
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.openapi.roots.impl.libraries.LibraryImpl
import com.intellij.openapi.roots.impl.libraries.LibraryTableImplUtil
import com.intellij.openapi.roots.libraries.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.isEmpty
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.VirtualFileUrlManagerImpl
import com.intellij.workspace.ide.WorkspaceModel
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibrary
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibraryImpl
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibraryModifiableModelImpl
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
  private val legacyBridgeModule: LegacyBridgeModule,
  private val moduleId: ModuleId,
  private val initialStorage: TypedEntityStorage,
  private val accessor: RootConfigurationAccessor
) : LegacyBridgeModifiableBase(diff), ModifiableRootModel, ModificationTracker {

  override fun getModificationCount(): Long = diff.modificationCount

  private val extensionsDisposable = Disposer.newDisposable()

  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManagerImpl.getInstance(project)

  private val extensionsDelegate = lazy {
    RootModelViaTypedEntityImpl.loadExtensions(storage = initialStorage, module = module, writable = true,
                                               parentDisposable = extensionsDisposable)
      .filterNot { compilerModuleExtensionClass.isAssignableFrom(it.javaClass) }
  }
  private val extensions by extensionsDelegate

  private val moduleEntityValue: CachedValue<ModuleEntity?> = CachedValue {
    it.resolve(moduleId)
  }

  private val moduleEntity: ModuleEntity
    get() = entityStoreOnDiff.cachedValue(moduleEntityValue) ?: error("Unable to resolve module by id '$moduleId'")

  private val moduleLibraryTable = ModuleLibraryTable(
    this, LegacyBridgeModuleRootComponent.getInstance(module).moduleLibraries.toList())

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

        val tableId = if (orderEntry.isModuleLevel) {
          LibraryTableId.ModuleLibraryTableId(moduleEntity.persistentId())
        }
        else toLibraryTableId(orderEntry.libraryLevel)

        val libraryEntityName = LegacyBridgeLibraryImpl.generateLibraryEntityName(orderEntry.libraryName) { existsName ->
          diff.resolve(LibraryId(existsName, tableId)) != null
        }

        if (orderEntry.isModuleLevel) {
          this.diff.addLibraryEntity(
            roots = emptyList(),
            tableId = tableId,
            name = libraryEntityName,
            excludedRoots = emptyList(),
            source = moduleEntity.entitySource
          )
        }

        updateDependencies { it + orderEntry.libraryDependencyItem }
        val orderEntryLibrary = orderEntry.library
        if (orderEntryLibrary is LegacyBridgeLibraryImpl) moduleLibraryTable.librariesToAdd += orderEntryLibrary
        Unit
      }

      is ModuleOrderEntry -> orderEntry.module?.let { addModuleOrderEntry(it); return } ?: error("Module is empty: $orderEntry")
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
    val libraryIdToFind = (library as LegacyBridgeLibrary).libraryId
    return orderEntries
      .filterIsInstance<LibraryOrderEntry>()
      .firstOrNull { libraryIdToFind == (it.library as? LegacyBridgeLibrary)?.libraryId }
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

    updateDependencies { dependencies ->
      val jdkItem = dependencies
        .firstOrNull { it is ModuleDependencyItem.InheritedSdkDependency || it is ModuleDependencyItem.SdkDependency }

      listOfNotNull(jdkItem, ModuleDependencyItem.ModuleSourceDependency)
    }

    for (contentRoot in moduleEntity.contentRoots) {
      diff.removeEntity(contentRoot)
    }

    for (sourceRoot in moduleEntity.sourceRoots) {
      diff.removeEntity(sourceRoot)
    }
  }

  fun collectChanges(): TypedEntityStorageBuilder? {
    assertModelIsLive()
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

    LegacyBridgeModuleRootComponent.getInstance(module).newModuleLibraries.clear()
    LegacyBridgeModuleRootComponent.getInstance(module).newModuleLibraries.addAll(moduleLibraryTable.librariesToAdd)
    // Do not clear `librariesToAdd`. Otherwise `getLibraries()` will return an empty list after the commit

    disposeWithoutLibraries()
    return diff
  }

  override fun commit() {
    val diff = collectChanges() ?: return

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
    if (isDisposed) return
    moduleLibraryTable.librariesToRemove.forEach { Disposer.dispose(it) }
    moduleLibraryTable.librariesToRemove.clear()
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

  private fun updateDependencies(updater: (List<ModuleDependencyItem>) -> List<ModuleDependencyItem>) {
    val newDependencies = updater(moduleEntity.dependencies)
    if (newDependencies == moduleEntity.dependencies) return

    diff.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) {
      dependencies = newDependencies
    }
  }

  private val modelValue = CachedValue { storage ->
    RootModelViaTypedEntityImpl(
      module = legacyBridgeModule,
      moduleEntityId = moduleId,
      storage = storage,
      filePointerProvider = LegacyBridgeFilePointerProvider.getInstance(legacyBridgeModule),
      itemUpdater = { index, transformer -> updateDependencies { dependencies ->
          val mutableList = dependencies.toMutableList()

          val old = mutableList[index]
          val new = transformer(old)
          mutableList[index] = new

          mutableList.toList()
        }
      },
      moduleLibraryTable = moduleLibraryTable,
      accessor = accessor,
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

  private class ModuleLibraryTable(
    private val modifiableModel: LegacyBridgeModifiableRootModel,
    private val initialLibraries: List<LegacyBridgeLibraryImpl>
  ) : LibraryTable, LibraryTable.ModifiableModel {

    val librariesToAdd = mutableListOf<LegacyBridgeLibraryImpl>()
    val librariesToRemove = mutableListOf<Library>()

    private val librariesValue = CachedValueWithParameter { _: TypedEntityStorage, (librariesToAdd, librariesToRemove): Pair<List<LegacyBridgeLibraryImpl>, List<Library>> ->
      val libs = initialLibraries.toMutableList<Library>()
      libs.removeAll(librariesToRemove)
      libs.addAll(librariesToAdd)
      return@CachedValueWithParameter libs.map { it.name to it }.toMap() to libs.toTypedArray()
    }

    private val libraries
      get() = modifiableModel.entityStoreOnDiff.cachedValue(librariesValue, librariesToAdd to librariesToRemove)

    override fun commit() {
      librariesToAdd.forEach { library ->
        val componentAsString = modifiableModel.serializeComponentAsString(LibraryImpl.PROPERTIES_ELEMENT, library.properties) ?: return@forEach
        library.libraryEntity?.getCustomProperties()?.let { property ->
          modifiableModel.diff.modifyEntity(ModifiableLibraryPropertiesEntity::class.java, property) {
            propertiesXmlTag = componentAsString
          }
        }
      }
    }

    override fun dispose() {
    }

    override fun isChanged(): Boolean = modifiableModel.isChanged

    override fun createLibrary(): Library = createLibrary(name = null)
    override fun createLibrary(name: String?): Library = createLibrary(name = name, type = null)
    override fun createLibrary(name: String?, type: PersistentLibraryKind<out LibraryProperties<*>>?): Library =
      createLibrary(name = name, type = type, externalSource = null)

    // TODO Support externalSource. Could it be different from module's?
    override fun createLibrary(name: String?,
                               type: PersistentLibraryKind<out LibraryProperties<*>>?,
                               externalSource: ProjectModelExternalSource?): Library {

      modifiableModel.assertModelIsLive()

      val tableId = LibraryTableId.ModuleLibraryTableId(modifiableModel.moduleEntity.persistentId())

      val libraryEntityName = LegacyBridgeLibraryImpl.generateLibraryEntityName(name) { existsName ->
        modifiableModel.diff.resolve(LibraryId(existsName, tableId)) != null
      }

      val libraryEntity = modifiableModel.diff.addLibraryEntity(
        roots = emptyList(),
        tableId = tableId,
        name = libraryEntityName,
        excludedRoots = emptyList(),
        source = modifiableModel.moduleEntity.entitySource
      )

      if (type != null) {
        modifiableModel.diff.addLibraryPropertiesEntity(
          library = libraryEntity,
          libraryType = type.kindId,
          propertiesXmlTag = null,
          source = libraryEntity.entitySource
        )
      }

      val libraryId = libraryEntity.persistentId()

      modifiableModel.updateDependencies {
        it + ModuleDependencyItem.Exportable.LibraryDependency(
          library = libraryId,
          exported = false,
          scope = ModuleDependencyItem.DependencyScope.COMPILE
        )
      }

      val moduleRootComponent = LegacyBridgeModuleRootComponent.getInstance(modifiableModel.module)

      val libraryImpl = LegacyBridgeLibraryImpl(
        libraryTable = moduleRootComponent.moduleLibraryTable,
        project = modifiableModel.project,
        initialId = libraryId,
        initialEntityStore = modifiableModel.entityStoreOnDiff,
        parent = moduleRootComponent
      )
      libraryImpl.modifiableModelFactory = { librarySnapshot , diff->
        LegacyBridgeLibraryModifiableModelImpl(
          originalLibrary = libraryImpl,
          originalLibrarySnapshot = librarySnapshot,
          diff = diff,
          committer = { _, diffBuilder ->
            modifiableModel.diff.addDiff(diffBuilder)
          }
        )
      }
      librariesToAdd.add(libraryImpl)
      return libraryImpl
    }

    override fun removeLibrary(library: Library) {
      modifiableModel.assertModelIsLive()
      library as LegacyBridgeLibrary

      val moduleLibraryTableId = library.libraryId.tableId as LibraryTableId.ModuleLibraryTableId
      if (moduleLibraryTableId.moduleId != modifiableModel.moduleId) {
        error("removeLibrary should be called on a module library from the same module. " +
              "Library moduleId: '${moduleLibraryTableId.moduleId}', this model moduleId: '${modifiableModel.moduleId}'")
      }

      val libraryEntity = library.libraryId.resolve(modifiableModel.entityStoreOnDiff.current)
                          ?: error("Unable to resolve module library by id: ${library.libraryId}")

      modifiableModel.updateDependencies { dependencies ->
        dependencies.filterNot { it is ModuleDependencyItem.Exportable.LibraryDependency && it.library == library.libraryId }
      }

      modifiableModel.diff.removeEntity(libraryEntity)

      if (librariesToAdd.remove(library)) {
        Disposer.dispose(library)
      }
      else {
        librariesToRemove.add(library)
      }
    }

    override fun getLibraries(): Array<Library> = libraries.second
    override fun getLibraryIterator(): Iterator<Library> = libraries.second.iterator()
    override fun getLibraryByName(name: String): Library? = libraries.second.firstOrNull { it.name == name }
    override fun getTableLevel(): String = LibraryTableImplUtil.MODULE_LEVEL
    override fun getPresentation(): LibraryTablePresentation = com.intellij.openapi.roots.impl.ModuleLibraryTable.MODULE_LIBRARY_TABLE_PRESENTATION
    override fun getModifiableModel(): LibraryTable.ModifiableModel = this

    override fun addListener(listener: LibraryTable.Listener) =
      throw UnsupportedOperationException("Not implemented for module-level library table")

    override fun addListener(listener: LibraryTable.Listener, parentDisposable: Disposable) =
      throw UnsupportedOperationException("Not implemented for module-level library table")

    override fun removeListener(listener: LibraryTable.Listener) =
      throw UnsupportedOperationException("Not implemented for module-level library table")
  }
}

fun toLibraryTableId(level: String) = when (level) {
  JpsLibraryTableSerializer.MODULE_LEVEL -> error("this method isn't supposed to be used for module-level libraries")
  JpsLibraryTableSerializer.PROJECT_LEVEL -> LibraryTableId.ProjectLibraryTableId
  else -> LibraryTableId.GlobalLibraryTableId(level)
}