// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.RootProvider.RootSetChangedListener
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TraceableDisposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.serialization.impl.LibraryNameGenerator
import com.intellij.platform.workspace.storage.CachedValue
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.projectModel.ProjectModelBundle
import com.intellij.util.EventDispatcher
import com.intellij.util.PathUtil
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.findLibraryEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots.ModuleLibraryTableBridge
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

interface LibraryBridge : LibraryEx {
  val libraryId: LibraryId

  @ApiStatus.Internal
  fun getModifiableModel(builder: MutableEntityStorage): LibraryEx.ModifiableModelEx
}

@ApiStatus.Internal
sealed interface LibraryOrigin {
  class OfProject(val project: Project) : LibraryOrigin
  class OfMachine(val eelMachine: EelMachine) : LibraryOrigin
}

@ApiStatus.Internal
class LibraryBridgeImpl(
  var libraryTable: LibraryTable,
  val origin: LibraryOrigin,
  initialId: LibraryId,
  initialEntityStorage: VersionedEntityStorage,
  private var targetBuilder: MutableEntityStorage?,
) : LibraryBridge, RootProvider, TraceableDisposable(true), Disposable {

  override fun getModule(): Module? = (libraryTable as? ModuleLibraryTableBridge)?.module

  var entityStorage: VersionedEntityStorage = initialEntityStorage
    set(value) {
      ApplicationManager.getApplication().assertWriteAccessAllowed()
      field = value
    }

  var entityId: LibraryId = initialId

  private var disposed = false

  internal fun cleanCachedValue() {
    entityStorage.clearCachedValue(librarySnapshotCached)
  }

  private val dispatcher = EventDispatcher.create(RootSetChangedListener::class.java)

  private val librarySnapshotCached = CachedValue { storage ->
    LibraryStateSnapshot(
      libraryEntity = storage.findLibraryEntity(this) ?: error("Cannot find entity for library with ID $entityId"),
      storage = storage,
      libraryTable = libraryTable
    )
  }

  internal val librarySnapshot: LibraryStateSnapshot
    get() {
      checkDisposed()
      return entityStorage.cachedValue(librarySnapshotCached)
    }

  override val libraryId: LibraryId
    get() = entityId

  override fun getTable(): LibraryTable? = if (libraryTable is ModuleLibraryTableBridge) null else libraryTable
  override fun getRootProvider(): RootProvider = this
  override fun getPresentableName(): String = getPresentableName(this)

  override fun toString(): String {
    return "Library '$name', roots: ${librarySnapshot.libraryEntity.roots}"
  }

  /**
   * **Please think twice before the usage.** This method was introduced to avoid redundant copying of
   * the storage. You can use it only if you are sure that you wouldn't roll back your changes, and
   * they will be applied by the parent modifiable model.
   */
  fun getModifiableModelToTargetBuilder(): LibraryEx.ModifiableModelEx {
    val mutableEntityStorage = targetBuilder ?: error("Unexpected state. Target builder has to be not null")
    return getModifiableModel(mutableEntityStorage)
  }

  override fun getModifiableModel(): LibraryEx.ModifiableModelEx {
    val storage = librarySnapshot.storage
    val immutable = when (storage) {
      is ImmutableEntityStorage -> storage
      is MutableEntityStorage -> storage.toSnapshot()
      else -> error("Unexpected storage $this")
    }
    return getModifiableModel(MutableEntityStorage.from(immutable))
  }

  override fun getModifiableModel(builder: MutableEntityStorage): LibraryEx.ModifiableModelEx {
    val virtualFileUrlManager = when (origin) {
      is LibraryOrigin.OfMachine -> GlobalWorkspaceModel.getInstance(origin.eelMachine).getVirtualFileUrlManager()
      is LibraryOrigin.OfProject -> WorkspaceModel.getInstance(origin.project).getVirtualFileUrlManager()
    }
    return LibraryModifiableModelBridgeImpl(this, librarySnapshot, builder, targetBuilder, virtualFileUrlManager, false)
  }

  override fun getSource(): Library? = null
  override fun getExternalSource(): ProjectModelExternalSource? = librarySnapshot.externalSource
  override fun getInvalidRootUrls(type: OrderRootType): List<String> = librarySnapshot.getInvalidRootUrls(type)
  override fun getKind(): PersistentLibraryKind<*>? = librarySnapshot.kind
  override fun getName(): String? = LibraryNameGenerator.getLegacyLibraryName(entityId)
  override fun getUrls(rootType: OrderRootType): Array<String> = librarySnapshot.getUrls(rootType)
  override fun getFiles(rootType: OrderRootType): Array<VirtualFile> = librarySnapshot.getFiles(rootType)
  override fun getProperties(): LibraryProperties<*>? = librarySnapshot.properties
  override fun getExcludedRoots(): Array<VirtualFile> = librarySnapshot.excludedRoots
  override fun getExcludedRootUrls(): Array<String> = librarySnapshot.excludedRootUrls
  override fun isJarDirectory(url: String): Boolean = librarySnapshot.isJarDirectory(url)
  override fun isJarDirectory(url: String, rootType: OrderRootType): Boolean = librarySnapshot.isJarDirectory(url, rootType)
  override fun isValid(url: String, rootType: OrderRootType): Boolean = librarySnapshot.isValid(url, rootType)
  override fun hasSameContent(library: Library): Boolean {
    if (this === library) return true
    if (library !is LibraryBridgeImpl) return false

    if (name != library.name) return false
    if (kind != library.kind) return false
    if (properties != library.properties) return false
    if (librarySnapshot.libraryEntity.roots != library.librarySnapshot.libraryEntity.roots) return false
    if (!excludedRoots.contentEquals(library.excludedRoots)) return false
    return true
  }

  override fun readExternal(element: Element?) = throw NotImplementedError()
  override fun writeExternal(element: Element) = throw NotImplementedError()

  override fun addRootSetChangedListener(listener: RootSetChangedListener) = dispatcher.addListener(listener)
  override fun addRootSetChangedListener(listener: RootSetChangedListener, parentDisposable: Disposable) {
    dispatcher.addListener(listener, parentDisposable)
  }

  override fun removeRootSetChangedListener(listener: RootSetChangedListener) = dispatcher.removeListener(listener)

  override fun isDisposed(): Boolean = disposed
  override fun dispose() {
    checkDisposed()

    disposed = true
    kill(null)
  }

  private fun checkDisposed() {
    if (isDisposed) {
      val libraryEntity = try {
        entityStorage.cachedValue(librarySnapshotCached).libraryEntity
      }
      catch (t: Throwable) {
        null
      }
      val isDisposedGlobally = libraryEntity?.let {
        val snapshot = when (origin) {
          is LibraryOrigin.OfMachine -> GlobalWorkspaceModel.getInstance(origin.eelMachine).currentSnapshot
          is LibraryOrigin.OfProject -> WorkspaceModel.getInstance(origin.project).currentSnapshot
        }
        snapshot.libraryMap.getDataByEntity(it)?.isDisposed
      }
      val message = """
        Library $entityId already disposed:
        Library id: $libraryId
        Entity: ${libraryEntity.run { "$name, $this" }}
        Is disposed in ${
        when (origin) {
          is LibraryOrigin.OfProject -> "project"; is LibraryOrigin.OfMachine -> "global (${origin.eelMachine})"
        }
      } model: ${isDisposedGlobally != false}
        Stack trace: $stackTrace
        """.trimIndent()
      try {
        throwDisposalError(message)
      }
      catch (e: Exception) {
        thisLogger().error(message, e)
        throw e
      }
    }
  }

  internal fun fireRootSetChanged() {
    dispatcher.multicaster.rootSetChanged(this)
  }

  fun setTargetBuilder(builder: MutableEntityStorage) {
    targetBuilder = builder
  }

  fun clearTargetBuilder() {
    targetBuilder = null
  }

  companion object {
    private val libraryRootTypes = ConcurrentFactoryMap.createMap<String, LibraryRootTypeId> { LibraryRootTypeId(it) }

    fun OrderRootType.toLibraryRootType(): LibraryRootTypeId = when (this) {
      OrderRootType.CLASSES -> LibraryRootTypeId.COMPILED
      OrderRootType.SOURCES -> LibraryRootTypeId.SOURCES
      else -> libraryRootTypes[name()]!!
    }

    internal fun getPresentableName(library: LibraryEx): @Nls(capitalization = Nls.Capitalization.Title) String {
      val name = library.name
      if (name != null) {
        return name
      }
      if (library.isDisposed) {
        return ProjectModelBundle.message("disposed.library.title")
      }
      val urls = library.getUrls(OrderRootType.CLASSES)
      if (urls.size > 0) {
        return PathUtil.getFileName(PathUtil.toPresentableUrl(urls[0]))
      }
      return ProjectModelBundle.message("empty.library.title")
    }

    /**
     * A temporary function which disposes instances of `Library`. All the usages should be eliminated to fix IJPL-183361.
     */
    @ApiStatus.Internal
    @JvmStatic
    fun disposeLibrary(library: Library) {
      Disposer.dispose(library as Disposable)
    }
  }
}
