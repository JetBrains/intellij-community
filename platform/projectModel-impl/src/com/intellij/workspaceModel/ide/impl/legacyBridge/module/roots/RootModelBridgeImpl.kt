// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.configurationStore.deserializeAndLoadState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.roots.ModuleExtension
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.impl.LegacyModuleExtensionRegistry
import com.intellij.openapi.roots.impl.ModuleOrderEnumerator
import com.intellij.openapi.roots.impl.RootModelBase
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.JDOMUtil
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleExtensionBridgeFactory
import org.jdom.Element
import org.jetbrains.annotations.NotNull
import java.util.*

internal class RootModelBridgeImpl(internal val moduleEntity: ModuleEntity?,
                                   private val storage: VersionedEntityStorage,
                                   private val itemUpdater: ((Int, (ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?,
                                   private val rootModel: ModuleRootModelBridge,
                                   internal val updater: (((MutableEntityStorage) -> Unit) -> Unit)?) : RootModelBase() {
  private val module: ModuleBridge = rootModel.moduleBridge

  private val extensions: Set<ModuleExtension> by lazy {
    loadExtensions(storage = storage, module = module, writable = false, diff = null)
  }

  private val orderEntriesArray: Array<OrderEntry> by lazy {
    val moduleEntity = moduleEntity ?: return@lazy emptyArray<OrderEntry>()
    moduleEntity.dependencies.mapIndexed { index, e ->
      toOrderEntry(e, index, rootModel, itemUpdater)
    }.toTypedArray()
  }

  val contentEntities: List<ContentRootEntity> by lazy {
    val moduleEntity = moduleEntity ?: return@lazy emptyList<ContentRootEntity>()
    return@lazy moduleEntity.contentRoots.toList()
  }

  private val contentEntriesList by lazy {
    val moduleEntity = moduleEntity ?: return@lazy emptyList<ContentEntryBridge>()
    val contentEntries = moduleEntity.contentRoots.toMutableList()

    // We used to sort content roots previously, but this affects performance too much
    // Please process unordered list of content roots, or perform the sorting on your side
    //contentEntries.sortBy { it.url.url }
    contentEntries.map { contentRoot ->
      ContentEntryBridge(rootModel, contentRoot.sourceRoots.toList(), contentRoot, updater)
    }
  }

  override fun getModule(): ModuleBridge = module

  override fun <T : Any?> getModuleExtension(klass: Class<T>): T? {
    return extensions.filterIsInstance(klass).firstOrNull()
  }

  override fun getOrderEntries() = orderEntriesArray

  override fun getContent() = contentEntriesList

  override fun orderEntries(): OrderEnumerator = ModuleOrderEnumerator(this, null)

  companion object {
    private val MODULE_EXTENSION_BRIDGE_FACTORY_EP = ExtensionPointName<ModuleExtensionBridgeFactory<*>>("com.intellij.workspaceModel.moduleExtensionBridgeFactory")

    internal fun toOrderEntry(
      item: ModuleDependencyItem,
      index: Int,
      rootModelBridge: ModuleRootModelBridge,
      updater: ((Int, (ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?
    ): OrderEntryBridge {
      return when (item) {
        is ModuleDependency -> ModuleOrderEntryBridge(rootModelBridge, index, item, updater)
        is LibraryDependency -> {
          LibraryOrderEntryBridge(rootModelBridge, index, item, updater)
        }
        is SdkDependency -> SdkOrderEntryBridge(rootModelBridge, index, item)
        is InheritedSdkDependency -> InheritedSdkOrderEntryBridge(rootModelBridge, index, item)
        is ModuleSourceDependency -> ModuleSourceOrderEntryBridge(rootModelBridge, index, item)
      }
    }

    internal fun loadExtensions(storage: VersionedEntityStorage,
                                module: ModuleBridge,
                                writable: Boolean,
                                diff: MutableEntityStorage?): Set<ModuleExtension> {
      val result = TreeSet<ModuleExtension> { o1, o2 ->
        Comparing.compare(o1.javaClass.name, o2.javaClass.name)
      }

      MODULE_EXTENSION_BRIDGE_FACTORY_EP.extensionList.mapTo(result) {
        it.createExtension(module = module, entityStorage = storage, diff = diff)
      }

      val moduleEntity = module.findModuleEntity(storage.current)
      val rootManagerElement = moduleEntity?.customImlData?.rootManagerTagCustomData?.let { JDOMUtil.load(it) }

      service<LegacyModuleExtensionRegistry>().forEachExtension { extensionEp -> 
        val readOnlyExtension = loadExtension(extensionEp.createInstance(module), rootManagerElement)

        if (writable) {
          val modifiableExtension = readOnlyExtension.getModifiableModel(true)
          result.add(modifiableExtension)
        }
        else {
          result.add(readOnlyExtension)
        }
      }

      return result
    }

    private fun loadExtension(extension: ModuleExtension, rootManagerElement: @NotNull Element?): @NotNull ModuleExtension {
      val readOnlyExtension = extension.getModifiableModel(false)

      if (rootManagerElement != null) {
        if (readOnlyExtension is PersistentStateComponent<*>) {
          deserializeAndLoadState(readOnlyExtension, rootManagerElement)
        }
        else {
          @Suppress("DEPRECATION")
          readOnlyExtension.readExternal(rootManagerElement)
        }
      }
      return readOnlyExtension
    }
  }
}
