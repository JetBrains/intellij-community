// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.ModuleOrderEnumerator
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.storage.CachedValue
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.toSnapshot
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.ide.impl.DisposableCachedValue
import com.intellij.workspaceModel.ide.impl.legacyBridge.RootConfigurationAccessorForWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.module.JpsModuleSourceRoot
import org.jetbrains.jps.model.module.JpsModuleSourceRootType

class ModuleRootComponentBridge(
  private val currentModule: Module
) : ModuleRootManagerEx(), Disposable, ModuleRootModelBridge {

  override val moduleBridge = currentModule as ModuleBridge

  private val orderRootsCache = OrderRootsCacheBridge(currentModule.project, currentModule)

  private val modelValue = DisposableCachedValue(
    { moduleBridge.entityStorage },
    CachedValue { storage ->
      RootModelBridgeImpl(
        moduleEntity = moduleBridge.findModuleEntity(storage),
        storage = moduleBridge.entityStorage,
        itemUpdater = null,
        // TODO
        rootModel = this,
        updater = null
      )
    }, "Root Model Bridge (${currentModule.name})", currentModule.project).also { Disposer.register(this, it) }

  internal val moduleLibraryTable: ModuleLibraryTableBridgeImpl = ModuleLibraryTableBridgeImpl(moduleBridge)

  fun getModuleLibraryTable(): ModuleLibraryTableBridge {
    return moduleLibraryTable
  }

  init {
    @Suppress("DEPRECATION")
    MODULE_EXTENSION_NAME.getPoint(moduleBridge).addExtensionPointListener(object : ExtensionPointListener<ModuleExtension> {
      override fun extensionAdded(extension: ModuleExtension, pluginDescriptor: PluginDescriptor) {
        dropRootModelCache()
      }

      override fun extensionRemoved(extension: ModuleExtension, pluginDescriptor: PluginDescriptor) {
        dropRootModelCache()
      }
    }, false, null)
  }

  private val model: RootModelBridgeImpl
    get() = modelValue.value

  override val storage: EntityStorage
    get() = moduleBridge.entityStorage.current

  override val accessor: RootConfigurationAccessor
    get() = RootConfigurationAccessor.DEFAULT_INSTANCE

  override fun getOrCreateJpsRootProperties(sourceRootUrl: VirtualFileUrl, creator: () -> JpsModuleSourceRoot): JpsModuleSourceRoot {
    return creator()
  }

  override fun removeCachedJpsRootProperties(sourceRootUrl: VirtualFileUrl) {
  }

  override fun dispose() = Unit

  override fun dropCaches() {
    orderRootsCache.clearCache()
    dropRootModelCache()
  }

  internal fun dropRootModelCache() {
    modelValue.dropCache()
  }

  override fun getModificationCountForTests(): Long = moduleBridge.entityStorage.version

  override fun getExternalSource(): ProjectModelExternalSource? =
    ExternalProjectSystemRegistry.getInstance().getExternalSource(module)

  override fun getFileIndex(): ModuleFileIndex = currentModule.getService(ModuleFileIndex::class.java)!!

  override fun getModifiableModel(): ModifiableRootModel = getModifiableModel(RootConfigurationAccessor.DEFAULT_INSTANCE)
  override fun getModifiableModel(accessor: RootConfigurationAccessor): ModifiableRootModel = ModifiableRootModelBridgeImpl(
    MutableEntityStorage.from(moduleBridge.entityStorage.current.toSnapshot()),
    moduleBridge,
    accessor)

  /**
   * This method is used in Project Structure dialog to ensure that changes made in {@link ModifiableModuleModel} after creation
   * of this {@link ModifiableRootModel} are available in its storage and references in its {@link OrderEntry} can be resolved properly.
   */
  override fun getModifiableModelForMultiCommit(accessor: RootConfigurationAccessor): ModifiableRootModel =
    getModifiableModelForMultiCommit(accessor, true)

  @ApiStatus.Internal
  fun getModifiableModelForMultiCommit(accessor: RootConfigurationAccessor, cacheStorageResult: Boolean): ModifiableRootModel = ModifiableRootModelBridgeImpl(
    (moduleBridge.diff as? MutableEntityStorage) ?: (accessor as? RootConfigurationAccessorForWorkspaceModel)?.actualDiffBuilder
    ?: MutableEntityStorage.from(moduleBridge.entityStorage.current.toSnapshot()),
    moduleBridge,
    accessor,
    cacheStorageResult)

  @ApiStatus.Internal
  fun getModifiableModelWithoutCaching(): ModifiableRootModel {
    return getModifiableModel(MutableEntityStorage.from(moduleBridge.entityStorage.current.toSnapshot()), RootConfigurationAccessor.DEFAULT_INSTANCE)
  }

  fun getModifiableModel(diff: MutableEntityStorage, accessor: RootConfigurationAccessor): ModifiableRootModel {
    return ModifiableRootModelBridgeImpl(diff, moduleBridge, accessor, false)
  }

  override fun getDependencies(): Array<Module> = moduleDependencies
  override fun getDependencies(includeTests: Boolean): Array<Module> = getModuleDependencies(includeTests = includeTests)

  override fun isDependsOn(module: Module): Boolean = orderEntries.any { it is ModuleOrderEntry && it.module == module }

  override fun getExcludeRoots(): Array<VirtualFile> = model.excludeRoots
  override fun orderEntries(): OrderEnumerator = ModuleOrderEnumerator(this, orderRootsCache)

  override fun <T : Any?> getModuleExtension(klass: Class<T>): T? {
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
    @JvmStatic
    fun getInstance(module: Module): ModuleRootComponentBridge = ModuleRootManager.getInstance(module) as ModuleRootComponentBridge
  }
}
