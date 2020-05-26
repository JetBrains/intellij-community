// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.ModuleOrderEnumerator
import com.intellij.openapi.roots.impl.OrderRootsCache
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspace.api.CachedValue
import com.intellij.workspace.api.DisposableCachedValue
import com.intellij.workspace.api.TypedEntityStorage
import com.intellij.workspace.api.TypedEntityStorageBuilder
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibraryImpl
import com.intellij.workspace.legacyBridge.typedModel.module.RootModelViaTypedEntityImpl
import org.jetbrains.jps.model.module.JpsModuleSourceRootType

class LegacyBridgeModuleRootComponent(
  private val currentModule: Module
) : ModuleRootManagerEx(), Disposable, LegacyBridgeModuleRootModel {

  override val legacyBridgeModule = currentModule as LegacyBridgeModule

  private val orderRootsCache =  OrderRootsCache(currentModule)

  internal val newModuleLibraries = mutableListOf<LegacyBridgeLibraryImpl>()

  private val modelValue = DisposableCachedValue(
    { legacyBridgeModule.entityStore },
    CachedValue { storage ->
      RootModelViaTypedEntityImpl(
        moduleEntityId = legacyBridgeModule.moduleEntityId,
        storage = storage,
        moduleLibraryTable = moduleLibraryTable,
        itemUpdater = null,
        // TODO
        rootModel = this,
        updater = null
      )
    }).also { Disposer.register(this, it) }

  internal val moduleLibraryTable: LegacyBridgeModuleLibraryTableImpl = LegacyBridgeModuleLibraryTableImpl(legacyBridgeModule)

  fun getModuleLibraryTable(): LegacyBridgeModuleLibraryTable {
    return moduleLibraryTable
  }

  init {
    MODULE_EXTENSION_NAME.getPoint(legacyBridgeModule).addExtensionPointListener(object : ExtensionPointListener<ModuleExtension?> {
      override fun extensionAdded(extension: ModuleExtension, pluginDescriptor: PluginDescriptor) {
        dropRootModelCache()
      }

      override fun extensionRemoved(extension: ModuleExtension, pluginDescriptor: PluginDescriptor) {
        dropRootModelCache()
      }
    }, false, null)
  }

  private val model: RootModelViaTypedEntityImpl
    get() = modelValue.value

  override val storage: TypedEntityStorage
    get() = legacyBridgeModule.entityStore.current

  override val accessor: RootConfigurationAccessor
    get() = RootConfigurationAccessor.DEFAULT_INSTANCE

  override fun dispose() = Unit

  override fun dropCaches() {
    orderRootsCache.clearCache()
    dropRootModelCache()
  }

  internal fun dropRootModelCache() {
    modelValue.dropCache()
  }

  override fun getModificationCountForTests(): Long = legacyBridgeModule.entityStore.version

  override fun getExternalSource(): ProjectModelExternalSource? =
    ExternalProjectSystemRegistry.getInstance().getExternalSource(module)

  override fun getFileIndex(): ModuleFileIndex = currentModule.getService(ModuleFileIndex::class.java)!!

  override fun getModifiableModel(): ModifiableRootModel = getModifiableModel(RootConfigurationAccessor.DEFAULT_INSTANCE)
  override fun getModifiableModel(accessor: RootConfigurationAccessor): ModifiableRootModel = LegacyBridgeModifiableRootModel(
    TypedEntityStorageBuilder.from(legacyBridgeModule.entityStore.current),
    legacyBridgeModule, legacyBridgeModule.moduleEntityId,
    legacyBridgeModule.entityStore.current, accessor)

  fun getModifiableModel(diff: TypedEntityStorageBuilder,
                         accessor: RootConfigurationAccessor): ModifiableRootModel = LegacyBridgeModifiableRootModel(diff,
                                                                                                                     legacyBridgeModule,
                                                                                                                     legacyBridgeModule.moduleEntityId,
                                                                                                                     legacyBridgeModule.entityStore.current,
                                                                                                                     accessor)


  override fun getDependencies(): Array<Module> = moduleDependencies
  override fun getDependencies(includeTests: Boolean): Array<Module> = getModuleDependencies(includeTests = includeTests)

  override fun isDependsOn(module: Module): Boolean = orderEntries.any { it is ModuleOrderEntry && it.module == module }

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
    @JvmStatic
    fun getInstance(module: Module): LegacyBridgeModuleRootComponent = ModuleRootManager.getInstance(module) as LegacyBridgeModuleRootComponent
  }
}
