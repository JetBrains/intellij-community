package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.ModuleOrderEnumerator
import com.intellij.openapi.roots.impl.OrderRootsCache
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablePresentation
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspace.api.*
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibraryImpl
import com.intellij.workspace.legacyBridge.typedModel.module.RootModelViaTypedEntityImpl
import org.jetbrains.jps.model.module.JpsModuleSourceRootType
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

class LegacyBridgeModuleRootComponent(
  private val currentModule: Module
) : ModuleRootManagerEx(), Disposable {

  private val legacyBridgeModule = currentModule as LegacyBridgeModule

  private val orderRootsCache =  OrderRootsCache(currentModule)

  internal val moduleLibraries = mutableListOf<LegacyBridgeLibraryImpl>()
  internal val newModuleLibraries = mutableListOf<LegacyBridgeLibraryImpl>()

  private val modelValue = DisposableCachedValue(
    { legacyBridgeModule.entityStore },
    CachedValue { storage ->
      RootModelViaTypedEntityImpl(
        module = legacyBridgeModule,
        moduleEntityId = legacyBridgeModule.moduleEntityId,
        storage = storage,
        filePointerProvider = LegacyBridgeFilePointerProvider.getInstance(legacyBridgeModule),
        itemUpdater = null,
        // TODO
        moduleLibraryTable = moduleLibraryTable,
        accessor = RootConfigurationAccessor(),
        updater = null
      )
    }).also { Disposer.register(this, it) }

  internal val moduleLibraryTable = object : LegacyBridgeModuleLibraryTable {
    override val module: Module = legacyBridgeModule
    override fun getLibraries(): Array<Library> = moduleLibraries.toTypedArray()
    override fun createLibrary(): Library = throw UnsupportedOperationException()
    override fun createLibrary(name: String?): Library = throw UnsupportedOperationException()
    override fun removeLibrary(library: Library): Nothing = throw UnsupportedOperationException()
    override fun getLibraryIterator(): Iterator<Library> = libraries.iterator()
    override fun getLibraryByName(name: String): Library? = moduleLibraries.firstOrNull { it.name == name }
    override fun getTableLevel(): String = JpsLibraryTableSerializer.MODULE_LEVEL
    override fun getPresentation(): LibraryTablePresentation = com.intellij.openapi.roots.impl.ModuleLibraryTable.MODULE_LIBRARY_TABLE_PRESENTATION
    override fun getModifiableModel(): LibraryTable.ModifiableModel = throw UnsupportedOperationException()
    override fun addListener(listener: LibraryTable.Listener): Nothing = throw UnsupportedOperationException()
    override fun addListener(listener: LibraryTable.Listener, parentDisposable: Disposable)
      = throw UnsupportedOperationException()
    override fun removeListener(listener: LibraryTable.Listener): Nothing = throw UnsupportedOperationException()
  }

  init {
    val moduleLibraryTableId = LibraryTableId.ModuleLibraryTableId(legacyBridgeModule.moduleEntityId)

    legacyBridgeModule.entityStore.current
      .entities(LibraryEntity::class)
      .filter { it.tableId == moduleLibraryTableId }
      .forEach { libraryEntity ->
        val library = createModuleLibrary(libraryEntity.persistentId())
        moduleLibraries.add(library)
      }
  }

  internal fun createModuleLibrary(libraryId: LibraryId) = LegacyBridgeLibraryImpl(
    libraryTable = moduleLibraryTable,
    project = currentModule.project,
    initialEntityStore = legacyBridgeModule.entityStore,
    initialId = libraryId,
    initialModifiableModelFactory = null,
    parent = this
  )

  private val model
    get() = modelValue.value

  override fun dispose() = Unit

  override fun dropCaches() = orderRootsCache.clearCache()

  override fun getModificationCountForTests(): Long = legacyBridgeModule.entityStore.version

  override fun getExternalSource(): ProjectModelExternalSource? =
    ExternalProjectSystemRegistry.getInstance().getExternalSource(module)

  override fun getFileIndex(): ModuleFileIndex = ModuleServiceManager.getService(currentModule, ModuleFileIndex::class.java)!!

  override fun getModifiableModel(): ModifiableRootModel = getModifiableModel(RootConfigurationAccessor())
  override fun getModifiableModel(accessor: RootConfigurationAccessor): ModifiableRootModel = LegacyBridgeModifiableRootModel(
    legacyBridgeModule, legacyBridgeModule.moduleEntityId,
    legacyBridgeModule.entityStore.current, accessor)

  override fun getDependencies(): Array<Module> = moduleDependencies
  override fun getDependencies(includeTests: Boolean): Array<Module> = getModuleDependencies(includeTests = includeTests)

  override fun isDependsOn(module: Module): Boolean = dependencyModuleNames.any { it == module.name }

  override fun getExcludeRoots(): Array<VirtualFile> = model.excludeRoots
  override fun orderEntries(): OrderEnumerator = ModuleOrderEnumerator(this, orderRootsCache)

  private val compilerModuleExtension by lazy {
    LegacyBridgeCompilerModuleExtension(legacyBridgeModule, entityStore = legacyBridgeModule.entityStore, diff = null)
  }

  private val compilerModuleExtensionClass = CompilerModuleExtension::class.java

  override fun <T : Any?> getModuleExtension(klass: Class<T>): T? {
    if (compilerModuleExtensionClass.isAssignableFrom(klass)) {
      @Suppress("UNCHECKED_CAST")
      return compilerModuleExtension as T
    }

    return model.getModuleExtension(klass)
  }

  override fun getDependencyModuleNames(): Array<String> = model.dependencyModuleNames
  override fun getModule(): Module = currentModule
  override fun isSdkInherited(): Boolean = model.isSdkInherited
  override fun getOrderEntries(): Array<OrderEntry> = model.orderEntries
  override fun getSourceRootUrls(): Array<String> = model.sourceRootUrls
  override fun getSourceRootUrls(includingTests: Boolean): Array<String> = model.getSourceRootUrls(includingTests)
  override fun getContentEntries(): Array<ContentEntry> = model.contentEntries
  override fun getExcludeRootUrls(): Array<String> = model.excludeRootUrls
  override fun <R : Any?> processOrder(policy: RootPolicy<R>, initialValue: R): R = model.processOrder(policy, initialValue)
  override fun getSdk(): Sdk? = model.sdk
  override fun getSourceRoots(): Array<VirtualFile> = model.sourceRoots
  override fun getSourceRoots(includingTests: Boolean): Array<VirtualFile> = model.getSourceRoots(includingTests)
  override fun getSourceRoots(rootType: JpsModuleSourceRootType<*>): MutableList<VirtualFile> = model.getSourceRoots(rootType)
  override fun getSourceRoots(rootTypes: MutableSet<out JpsModuleSourceRootType<*>>): MutableList<VirtualFile> = model.getSourceRoots(
    rootTypes)

  override fun getContentRoots(): Array<VirtualFile> = model.contentRoots
  override fun getContentRootUrls(): Array<String> = model.contentRootUrls
  override fun getModuleDependencies(): Array<Module> = model.moduleDependencies
  override fun getModuleDependencies(includeTests: Boolean): Array<Module> = model.getModuleDependencies(includeTests)

  companion object {
    fun getInstance(module: Module): LegacyBridgeModuleRootComponent = ModuleRootManager.getInstance(module) as LegacyBridgeModuleRootComponent
  }
}
