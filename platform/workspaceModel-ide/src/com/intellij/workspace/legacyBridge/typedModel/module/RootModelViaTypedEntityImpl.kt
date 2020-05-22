// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.legacyBridge.typedModel.module

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
import com.intellij.workspace.api.*
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeCompilerModuleExtension
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModule
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModuleRootModel
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeLibrary
import org.jdom.Element
import org.jetbrains.annotations.NotNull
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.HashMap

internal class RootModelViaTypedEntityImpl(internal val moduleEntityId: PersistentEntityId<ModuleEntity>,
                                           val storage: TypedEntityStorage,
                                           private val moduleLibraryTable: LibraryTable,
                                           private val itemUpdater: ((Int, (ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?,
                                           private val rootModel: LegacyBridgeModuleRootModel,
                                           internal val updater: (((TypedEntityStorageDiffBuilder) -> Unit) -> Unit)?) : RootModelBase(), Disposable {
  private val module: LegacyBridgeModule = rootModel.legacyBridgeModule

  private val extensions by lazy {
    loadExtensions(storage = storage, module = module, writable = false, parentDisposable = this)
  }

  val moduleEntity: ModuleEntity? = storage.resolve(moduleEntityId)

  private val orderEntriesArray: Array<OrderEntry>
    get() {
      // This variable should not be cached unless
      //   com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModuleRootComponent.moduleLibraries is mutable
      val moduleEntity = storage.resolve(moduleEntityId) ?: return emptyArray()
      return moduleEntity.dependencies.mapIndexed { index, e ->
        toOrderEntry(e, index)
      }.toTypedArray()
    }

  val contentEntities: List<ContentRootEntity> by lazy {
    val moduleEntity = storage.resolve(moduleEntityId) ?: return@lazy emptyList<ContentRootEntity>()
    return@lazy moduleEntity.contentRoots.toList()
  }

  private val contentEntriesList by lazy {
    val moduleEntity = storage.resolve(moduleEntityId) ?: return@lazy emptyList<ContentEntryViaTypedEntity>()
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

    contentEntries.map { contentRoot ->
      ContentEntryViaTypedEntity(rootModel, contentUrlToSourceRoots[contentRoot.url] ?: emptyList(), contentRoot, updater)
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

  override fun getModule(): LegacyBridgeModule = module

  // TODO Deduplicate this code with other two root model implementations
  private val compilerModuleExtension by lazy {
    LegacyBridgeCompilerModuleExtension(module, entityStore = EntityStoreOnStorage(storage), diff = null)
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
      is ModuleDependencyItem.Exportable.ModuleDependency -> ModuleOrderEntryViaTypedEntity(rootModel, index, item, updater)
      is ModuleDependencyItem.Exportable.LibraryDependency -> {
        val library = moduleLibraryTable.libraries.firstOrNull { (it as? LegacyBridgeLibrary)?.libraryId == item.library }
        LibraryOrderEntryViaTypedEntity(rootModel, index, item, library, updater)
      }
      is ModuleDependencyItem.SdkDependency -> SdkOrderEntryViaTypedEntity(rootModel, index, item)
      is ModuleDependencyItem.InheritedSdkDependency -> InheritedSdkOrderEntryViaTypedEntity(rootModel, index, item)
      is ModuleDependencyItem.ModuleSourceDependency -> ModuleSourceOrderEntryViaTypedEntity(rootModel, index, item)
    }
  }

  companion object {
    internal fun loadExtensions(storage: TypedEntityStorage,
                                module: LegacyBridgeModule,
                                writable: Boolean,
                                parentDisposable: Disposable): Set<ModuleExtension> {

      val result = TreeSet<ModuleExtension> { o1, o2 ->
        Comparing.compare(o1.javaClass.name, o2.javaClass.name)
      }

      val moduleEntity = storage.resolve(module.moduleEntityId)
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

    internal fun loadExtension(extension: ModuleExtension,
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
