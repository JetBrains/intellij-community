package com.intellij.workspace.legacyBridge.typedModel.module

import com.intellij.configurationStore.deserializeAndLoadState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.rd.attachChild
import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.openapi.roots.ModuleExtension
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.impl.ModuleOrderEnumerator
import com.intellij.openapi.roots.impl.RootConfigurationAccessor
import com.intellij.openapi.roots.impl.RootModelBase
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.workspace.api.*
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeCompilerModuleExtension
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeFilePointerProvider
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModule
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.HashMap

class RootModelViaTypedEntityImpl(internal val moduleEntityId: PersistentEntityId<ModuleEntity>,
                                  internal val storage: TypedEntityStorage,
                                  private val module: LegacyBridgeModule,
                                  private val moduleLibraryTable: LibraryTable,
                                  private val itemUpdater: ((Int, (ModuleDependencyItem) -> ModuleDependencyItem) -> Unit)?,
                                  internal val filePointerProvider: LegacyBridgeFilePointerProvider,
                                  internal val accessor: RootConfigurationAccessor,
                                  internal val updater: (((TypedEntityStorageDiffBuilder) -> Unit) -> Unit)?) : RootModelBase(), Disposable {

  private val extensions by lazy {
    loadExtensions(storage = storage, module = module, writable = false, parentDisposable = this)
  }

  private val orderEntriesArray by lazy {
    val moduleEntity = storage.resolve(moduleEntityId) ?: return@lazy emptyArray<OrderEntry>()
    moduleEntity.dependencies.mapIndexed { index, e ->
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
        .find { VfsUtil.isEqualOrAncestor(it.url.url, sourceRoot.url.url) }

      val contentEntry = existingContentEntry ?: object : ContentRootEntity {
        override val url: VirtualFileUrl
          get() = sourceRoot.url

        override val excludedUrls: List<VirtualFileUrl>
          get() = emptyList()

        override val excludedPatterns: List<String>
          get() = emptyList()

        override val module: ModuleEntity
          get() = moduleEntity

        override val entitySource: EntitySource
          get() = moduleEntity.entitySource

        override fun hasEqualProperties(e: TypedEntity): Boolean = throw UnsupportedOperationException()
      }.also { contentEntries.add(it) }

      contentEntry.url
    }

    contentEntries.map { contentRoot ->
      ContentEntryViaTypedEntity(this, contentUrlToSourceRoots[contentRoot.url] ?: emptyList(), contentRoot)
    }
  }

  private val disposed = AtomicReference<Throwable>(null)
  override fun dispose() {
    val disposedStackTrace = disposed.getAndSet(Throwable())
    if (disposedStackTrace != null) throw IllegalStateException("${javaClass.name} was already disposed", disposedStackTrace)
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
      is ModuleDependencyItem.Exportable.ModuleDependency -> ModuleOrderEntryViaTypedEntity(this, index, item, updater)
      is ModuleDependencyItem.Exportable.LibraryDependency -> LibraryOrderEntryViaTypedEntity(this, index, item, moduleLibraryTable, updater)
      is ModuleDependencyItem.SdkDependency -> SdkOrderEntryViaTypedEntity(this, index, item)
      is ModuleDependencyItem.InheritedSdkDependency -> InheritedSdkOrderEntryViaTypedEntity(this, index, item)
      is ModuleDependencyItem.ModuleSourceDependency -> ModuleSourceOrderEntryViaTypedEntity(this, index, item)
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

      for (extension in ModuleExtension.EP_NAME.getExtensions(module)) {
        val readOnlyExtension = extension.getModifiableModel(false).also { parentDisposable.attachChild(it) }

        if (rootManagerElement != null) {
          if (readOnlyExtension is PersistentStateComponent<*>) {
            deserializeAndLoadState(readOnlyExtension, rootManagerElement)
          }
          else {
            @Suppress("DEPRECATION")
            readOnlyExtension.readExternal(rootManagerElement)
          }
        }

        if (writable) {
          val modifiableExtension = readOnlyExtension.getModifiableModel(true).also { parentDisposable.attachChild(it) }
          result.add(modifiableExtension)
        } else {
          result.add(readOnlyExtension)
        }
      }

      return result
    }
  }
}
