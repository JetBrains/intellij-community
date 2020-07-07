// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.configurationStore.deserializeAndLoadState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.roots.*
import com.intellij.openapi.roots.impl.ModuleOrderEnumerator
import com.intellij.openapi.roots.impl.RootModelBase
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.CompilerModuleExtensionBridge
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge.Companion.findModuleEntity
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.VersionedEntityStorageOnStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageDiffBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.FakeContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleDependencyItem
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import org.jdom.Element
import org.jetbrains.annotations.NotNull
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.HashMap

internal class RootModelBridgeImpl(internal val moduleEntity: ModuleEntity?,
                                   val storage: WorkspaceEntityStorage,
                                   private val moduleLibraryTable: LibraryTable,
                                   private val itemUpdater: ((Int, (ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?,
                                   private val rootModel: ModuleRootModelBridge,
                                   internal val updater: (((WorkspaceEntityStorageDiffBuilder) -> Unit) -> Unit)?) : RootModelBase(), Disposable {
  private val module: ModuleBridge = rootModel.moduleBridge

  private val extensions by lazy {
    loadExtensions(storage = storage, module = module, writable = false, parentDisposable = this)
  }

  private val orderEntriesArray: Array<OrderEntry> by lazy {
    val moduleEntity = moduleEntity ?: return@lazy emptyArray<OrderEntry>()
    moduleEntity.dependencies.mapIndexed { index, e ->
      toOrderEntry(e, index)
    }.toTypedArray()
  }

  val contentEntities: List<ContentRootEntity> by lazy {
    val moduleEntity = moduleEntity ?: return@lazy emptyList<ContentRootEntity>()
    return@lazy moduleEntity.contentRoots.toList()
  }

  private val contentEntriesList by lazy {
    val moduleEntity = moduleEntity ?: return@lazy emptyList<ContentEntryBridge>()
    val contentEntries = moduleEntity.contentRoots.toMutableList()

    val contentUrlToSourceRoots = moduleEntity.sourceRoots.groupByTo(HashMap()) { sourceRoot ->

      // Order contentEntries so most nested will be selected
      // TODO It's very slow. Probably it's better to sort by number of components in VirtualFileUrl or something
      val existingContentEntry = contentEntries
        .sortedByDescending { it.url.url.length }
        .find { it.url.isEqualOrParentOf(sourceRoot.url) }

      val contentEntry = existingContentEntry ?: FakeContentRootEntity(sourceRoot.url, moduleEntity).also { contentEntries.add(it) }

      contentEntry.url
    }

    contentEntries.sortBy { it.url.url }
    contentEntries.map { contentRoot ->
      ContentEntryBridge(rootModel, contentUrlToSourceRoots[contentRoot.url] ?: emptyList(), contentRoot, updater)
    }
  }

  private var disposedStackTrace: Throwable? = null
  private val isDisposed = AtomicBoolean(false)

  override fun dispose() {
    val alreadyDisposed = isDisposed.getAndSet(true)
    if (alreadyDisposed) {
      val trace = disposedStackTrace
      if (trace != null) {
        throw IllegalStateException("${javaClass.name} was already disposed", trace)
      }
      else throw IllegalStateException("${javaClass.name} was already disposed")
    } else if (Disposer.isDebugMode()) {
      disposedStackTrace = Throwable()
    }
  }

  override fun getModule(): ModuleBridge = module

  // TODO Deduplicate this code with other two root model implementations
  private val compilerModuleExtension by lazy {
    CompilerModuleExtensionBridge(module, entityStorage = VersionedEntityStorageOnStorage(storage), diff = null)
  }

  private val compilerModuleExtensionClass = CompilerModuleExtension::class.java

  override fun <T : Any?> getModuleExtension(klass: Class<T>): T? {
    if (compilerModuleExtensionClass.isAssignableFrom(klass)) {
      @Suppress("UNCHECKED_CAST")
      return compilerModuleExtension as T
    }

    return extensions.filterIsInstance(klass).firstOrNull()
  }

  override fun getOrderEntries() = orderEntriesArray

  override fun getContent() = contentEntriesList

  override fun orderEntries(): OrderEnumerator = ModuleOrderEnumerator(this, null)

  private fun toOrderEntry(
    item: ModuleDependencyItem,
    index: Int
  ): OrderEntry {
    val itemUpdaterLocal = itemUpdater

    val updater: (((ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)? = if (itemUpdaterLocal != null) { func: (ModuleDependencyItem) -> ModuleDependencyItem ->
      itemUpdaterLocal(index, func)
    }
    else null

    return when (item) {
      is ModuleDependencyItem.Exportable.ModuleDependency -> ModuleOrderEntryBridge(rootModel, index, item, updater)
      is ModuleDependencyItem.Exportable.LibraryDependency -> {
        LibraryOrderEntryBridge(rootModel, index, item, updater)
      }
      is ModuleDependencyItem.SdkDependency -> SdkOrderEntryBridge(rootModel, index, item)
      is ModuleDependencyItem.InheritedSdkDependency -> InheritedSdkOrderEntryBridge(rootModel, index, item)
      is ModuleDependencyItem.ModuleSourceDependency -> ModuleSourceOrderEntryBridge(rootModel, index, item)
    }
  }

  companion object {
    internal fun loadExtensions(storage: WorkspaceEntityStorage,
                                module: ModuleBridge,
                                writable: Boolean,
                                parentDisposable: Disposable): Set<ModuleExtension> {

      val result = TreeSet<ModuleExtension> { o1, o2 ->
        Comparing.compare(o1.javaClass.name, o2.javaClass.name)
      }

      val moduleEntity = storage.findModuleEntity(module)
      val rootManagerElement = moduleEntity?.customImlData?.rootManagerTagCustomData?.let { JDOMUtil.load(it) }

      for (extension in ModuleRootManagerEx.MODULE_EXTENSION_NAME.getExtensions(module)) {
        val readOnlyExtension = loadExtension(extension, parentDisposable, rootManagerElement)

        if (writable) {
          val modifiableExtension = readOnlyExtension.getModifiableModel(true).also {
            Disposer.register(parentDisposable, it)
          }
          result.add(modifiableExtension)
        } else {
          result.add(readOnlyExtension)
        }
      }

      return result
    }

    private fun loadExtension(extension: ModuleExtension,
                              parentDisposable: Disposable,
                              rootManagerElement: @NotNull Element?): @NotNull ModuleExtension {
      val readOnlyExtension = extension.getModifiableModel(false).also {
        Disposer.register(parentDisposable, it)
      }

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
