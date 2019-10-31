package com.intellij.workspace.legacyBridge.libraries.libraries

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TraceableDisposable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.WorkspaceModel
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeFilePointerProviderImpl
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeModuleLibraryTable
import com.intellij.workspace.legacyBridge.typedModel.library.LibraryViaTypedEntity
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus

interface LegacyBridgeLibrary : LibraryEx {
  val libraryId: LibraryId
}

@ApiStatus.Internal
class LegacyBridgeLibraryImpl(
  private val libraryTable: LibraryTable,
  val project: Project,
  initialId: LibraryId,
  initialEntityStore: TypedEntityStore,
  initialModifiableModelFactory: ((LibraryViaTypedEntity) -> LegacyBridgeLibraryModifiableModelImpl)?,
  parent: Disposable
) : LegacyBridgeLibrary, RootProvider, TraceableDisposable(true) {

  override fun getModule(): Module? = (libraryTable as? LegacyBridgeModuleLibraryTable)?.module

  init {
    Disposer.register(parent, this)
  }

  private val filePointerProvider = LegacyBridgeFilePointerProviderImpl().also { Disposer.register(this, it) }

  var entityStore: TypedEntityStore = initialEntityStore
    internal set(value) {
      ApplicationManager.getApplication().assertWriteAccessAllowed()
      field = value
    }

  var entityId: LibraryId = initialId
    internal set(value) {
      ApplicationManager.getApplication().assertWriteAccessAllowed()
      field = value
    }

  private var disposed = false

  // null to update project model via ProjectModelUpdater
  var modifiableModelFactory = initialModifiableModelFactory

  private val libraryEntityValue = CachedValueWithParameter { storage, id: LibraryId ->
    storage.resolve(id)
  }

  private val libraryEntity
    get() = entityStore.cachedValue(libraryEntityValue, entityId)

  private val snapshotValue = CachedValueWithParameter { storage, id: LibraryId ->
    LibraryViaTypedEntity(
      libraryEntity = storage.resolve(id) ?: object : LibraryEntity {
        override val entitySource: EntitySource
          get() = throw NotImplementedError()

        override fun hasEqualProperties(e: TypedEntity): Boolean {
          return e is LibraryEntity && e.name == name && e.roots.isEmpty() && e.excludedRoots.isEmpty()
        }

        override val tableId: LibraryTableId
          get() = throw NotImplementedError()
        override val name: String
          get() = id.name
        override val roots: List<LibraryRoot>
          get() = emptyList()
        override val excludedRoots: List<VirtualFileUrl>
          get() = emptyList()
        override fun <R : TypedEntity> referrers(entityClass: Class<R>, propertyName: String) = emptySequence<R>()
      },
      storage = storage,
      libraryTable = libraryTable,
      filePointerProvider = filePointerProvider,
      modifiableModelFactory = modifiableModelFactory ?: { libraryImpl ->
        LegacyBridgeLibraryModifiableModelImpl(libraryImpl, committer = { _, diff ->
          WorkspaceModel.getInstance(project).updateProjectModel {
            it.addDiff(diff)
          }
        })
      }
    )
  }

  private val snapshot: LibraryViaTypedEntity
    get() {
      checkDisposed()
      return entityStore.cachedValue(snapshotValue, entityId)
    }

  override val libraryId: LibraryId
    get() = entityId
  override fun getTable(): LibraryTable? = if (libraryTable is LegacyBridgeModuleLibraryTable) null else libraryTable
  override fun getRootProvider(): RootProvider = this

  override fun getModifiableModel(): LibraryEx.ModifiableModelEx = snapshot.modifiableModel
  override fun getExternalSource(): ProjectModelExternalSource? = snapshot.externalSource
  override fun getInvalidRootUrls(type: OrderRootType): List<String> = snapshot.getInvalidRootUrls(type)
  override fun getKind(): PersistentLibraryKind<*>? = snapshot.kind
  override fun getName(): String? = if (entityId.name.startsWith(LibraryEntity.UNNAMED_LIBRARY_NAME_PREFIX)) null else entityId.name
  override fun getUrls(rootType: OrderRootType): Array<String> = snapshot.getUrls(rootType)
  override fun getFiles(rootType: OrderRootType): Array<VirtualFile> = snapshot.getFiles(rootType)
  override fun getProperties(): LibraryProperties<*>? = snapshot.properties
  override fun getExcludedRoots(): Array<VirtualFile> = snapshot.excludedRoots
  override fun getExcludedRootUrls(): Array<String> = snapshot.excludedRootUrls
  override fun isJarDirectory(url: String): Boolean = snapshot.isJarDirectory(url)
  override fun isJarDirectory(url: String, rootType: OrderRootType): Boolean = snapshot.isJarDirectory(url, rootType)
  override fun isValid(url: String, rootType: OrderRootType): Boolean = snapshot.isValid(url, rootType)

  override fun readExternal(element: Element?) = throw NotImplementedError()
  override fun writeExternal(element: Element) = snapshot.writeExternal(element)

  override fun addRootSetChangedListener(listener: RootProvider.RootSetChangedListener) {
    TODO()
  }

  override fun addRootSetChangedListener(listener: RootProvider.RootSetChangedListener, parentDisposable: Disposable) {
    TODO()
  }

  override fun removeRootSetChangedListener(listener: RootProvider.RootSetChangedListener) {
    TODO()
  }

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
    return libraryEntity == otherLib.libraryEntity
  }

  override fun hashCode(): Int = libraryEntity.hashCode()
}
