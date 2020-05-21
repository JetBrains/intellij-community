package com.intellij.workspace.legacyBridge.libraries.libraries

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.util.EventDispatcher
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.WorkspaceModel
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeFilePointerProviderImpl
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModuleLibraryTable
import com.intellij.workspace.legacyBridge.typedModel.library.LibraryViaTypedEntity
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus

interface LegacyBridgeLibrary : LibraryEx {
  val libraryId: LibraryId

  @ApiStatus.Internal
  fun getModifiableModel(builder: TypedEntityStorageBuilder): LibraryEx.ModifiableModelEx
}

@ApiStatus.Internal
internal class LegacyBridgeLibraryImpl(
  private val libraryTable: LibraryTable,
  val project: Project,
  initialId: LibraryId,
  initialEntityStore: TypedEntityStore,
  parent: Disposable,
  private var targetBuilder: TypedEntityStorageDiffBuilder?
) : LegacyBridgeLibrary, RootProvider, TraceableDisposable(true) {

  init {
    Disposer.register(parent, this)
  }

  override fun getModule(): Module? = (libraryTable as? LegacyBridgeModuleLibraryTable)?.module

  val filePointerProvider = LegacyBridgeFilePointerProviderImpl(project).also { Disposer.register(this, it) }

  var entityStore: TypedEntityStore = initialEntityStore
    internal set(value) {
      ApplicationManager.getApplication().assertWriteAccessAllowed()
      field = value
    }

  internal var entityId: LibraryId = initialId

  private var disposed = false

  // null to update project model via ProjectModelUpdater
  var modifiableModelFactory: ((LibraryViaTypedEntity, TypedEntityStorageBuilder) -> LegacyBridgeLibraryModifiableModelImpl)? = null

  internal fun cleanCachedValue() {
    entityStore.clearCachedValue(librarySnapshotCached, entityId)
  }

  private val dispatcher = EventDispatcher.create(RootSetChangedListener::class.java)

  private val librarySnapshotCached: CachedValueWithParameter<LibraryId, LibraryViaTypedEntity> = CachedValueWithParameter { storage, id: LibraryId ->
    LibraryViaTypedEntity(
      libraryEntity = storage.resolve(id) ?: FakeLibraryEntity(id.name),
      filePointerProvider = filePointerProvider,
      storage = storage,
      libraryTable = libraryTable
    )
  }

  internal val librarySnapshot: LibraryViaTypedEntity
    get() {
      checkDisposed()
      return entityStore.cachedValue(librarySnapshotCached, entityId)
    }

  override val libraryId: LibraryId
    get() = entityId
  override fun getTable(): LibraryTable? = if (libraryTable is LegacyBridgeModuleLibraryTable) null else libraryTable
  override fun getRootProvider(): RootProvider = this

  override fun getModifiableModel(): LibraryEx.ModifiableModelEx {
    return getModifiableModel(TypedEntityStorageBuilder.from(librarySnapshot.storage))
  }
  override fun getModifiableModel(builder: TypedEntityStorageBuilder): LibraryEx.ModifiableModelEx {
    return LegacyBridgeLibraryModifiableModelImpl(this, librarySnapshot, builder, targetBuilder)
  }
  override fun getSource(): Library? = null
  override fun getExternalSource(): ProjectModelExternalSource? = librarySnapshot.externalSource
  override fun getInvalidRootUrls(type: OrderRootType): List<String> = librarySnapshot.getInvalidRootUrls(type)
  override fun getKind(): PersistentLibraryKind<*>? = librarySnapshot.kind
  override fun getName(): String? = getLegacyLibraryName(entityId)
  override fun getUrls(rootType: OrderRootType): Array<String> = librarySnapshot.getUrls(rootType)
  override fun getFiles(rootType: OrderRootType): Array<VirtualFile> = librarySnapshot.getFiles(rootType)
  override fun getProperties(): LibraryProperties<*>? = librarySnapshot.properties
  override fun getExcludedRoots(): Array<VirtualFile> = librarySnapshot.excludedRoots
  override fun getExcludedRootUrls(): Array<String> = librarySnapshot.excludedRootUrls
  override fun isJarDirectory(url: String): Boolean = librarySnapshot.isJarDirectory(url)
  override fun isJarDirectory(url: String, rootType: OrderRootType): Boolean = librarySnapshot.isJarDirectory(url, rootType)
  override fun isValid(url: String, rootType: OrderRootType): Boolean = librarySnapshot.isValid(url, rootType)

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
      throwDisposalError("library $entityId already disposed: $stackTrace")
    }
  }

  override fun equals(other: Any?): Boolean {
    val otherLib = other as? LegacyBridgeLibraryImpl ?: return false
    return librarySnapshot.libraryEntity == otherLib.librarySnapshot.libraryEntity
  }

  override fun hashCode(): Int = librarySnapshot.libraryEntity.hashCode()

  internal fun fireRootSetChanged() {
    dispatcher.multicaster.rootSetChanged(this)
  }

  fun clearTargetBuilder() {
    targetBuilder = null
  }

  companion object {
    private const val UNNAMED_LIBRARY_NAME_PREFIX = "#"
    private const val UNIQUE_INDEX_LIBRARY_NAME_SUFFIX = "-d1a6f608-UNIQUE-INDEX-f29c-4df6-"

    fun getLegacyLibraryName(libraryId: LibraryId): String? {
      if (libraryId.name.startsWith(UNNAMED_LIBRARY_NAME_PREFIX)) return null
      if (libraryId.name.contains(UNIQUE_INDEX_LIBRARY_NAME_SUFFIX)) return libraryId.name.substringBefore(UNIQUE_INDEX_LIBRARY_NAME_SUFFIX)
      return libraryId.name
    }

    fun generateLibraryEntityName(legacyLibraryName: String?, exists: (String) -> Boolean): String {
      if (legacyLibraryName == null) {
        // TODO Make it O(1) if required

        var index = 1
        while (true) {
          val candidate = "$UNNAMED_LIBRARY_NAME_PREFIX$index"
          if (!exists(candidate)) {
            return candidate
          }

          index++
        }

        @Suppress("UNREACHABLE_CODE")
        error("Unable to suggest unique name for unnamed module library")
      }

      if (!exists(legacyLibraryName)) return legacyLibraryName

      var index = 1
      while (true) {
        val candidate = "$legacyLibraryName${UNIQUE_INDEX_LIBRARY_NAME_SUFFIX}$index"
        if (!exists(candidate)) {
          return candidate
        }

        index++
      }
    }
  }

  class FakeLibraryEntity(name: String) : LibraryEntity(LibraryTableId.ProjectLibraryTableId, name, emptyList(), emptyList()) {
    override var entitySource: EntitySource
      get() = throw NotImplementedError()
      set(value) {
        throw NotImplementedError()
      }

    override fun <R : TypedEntity> referrers(entityClass: Class<R>, propertyName: String): Sequence<R> = emptySequence()
    override val tableId: LibraryTableId
      get() = throw NotImplementedError()

    override fun hasEqualProperties(e: TypedEntity): Boolean {
      return e is LibraryEntity && e.name == name && e.roots.isEmpty() && e.excludedRoots.isEmpty()
    }

    override fun toString(): String = "FakeLibraryEntity($name)"

    override fun equals(other: Any?): Boolean {
      if (other !is FakeLibraryEntity) return false

      return this.name == other.name
    }

    override fun hashCode(): Int = name.hashCode()
  }
}
