// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablePresentation
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.projectModel.ProjectModelBundle
import com.intellij.util.EventDispatcher
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryId
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryTableId
import com.intellij.workspaceModel.ide.impl.bracket
import com.intellij.workspaceModel.ide.impl.executeOrQueueOnDispatchThread
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.legacyBridge.ProjectLibraryTableBridge
import com.intellij.workspaceModel.storage.*

internal class ProjectLibraryTableBridgeImpl(
  private val parentProject: Project
) : ProjectLibraryTableBridge, Disposable {

  private val LOG = Logger.getInstance(javaClass)

  private val entityStorage: VersionedEntityStorage = WorkspaceModel.getInstance(parentProject).entityStorage

  private val dispatcher = EventDispatcher.create(LibraryTable.Listener::class.java)

  private val libraryArrayValue = CachedValue<Array<Library>> { storage ->
    storage.entities(LibraryEntity::class.java).filter { it.tableId == LibraryTableId.ProjectLibraryTableId }
      .mapNotNull { storage.libraryMap.getDataByEntity(it) }
      .toList().toTypedArray()
  }

  init {
    val messageBusConnection = project.messageBus.connect(this)

    WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(messageBusConnection, object : WorkspaceModelChangeListener {
      override fun beforeChanged(event: VersionedStorageChanged) {
        val changes = event.getChanges(LibraryEntity::class.java).filterProjectLibraryChanges()
          .filterIsInstance<EntityChange.Removed<LibraryEntity>>()
        if (changes.isEmpty()) return

        executeOrQueueOnDispatchThread {
          LOG.bracket("ProjectLibraryTable.beforeChanged") {
            for (change in changes) {
              val library = event.storageBefore.libraryMap.getDataByEntity(change.entity)
              if (library != null) {
                dispatcher.multicaster.beforeLibraryRemoved(library)
              }
            }
          }
        }
      }

      override fun changed(event: VersionedStorageChanged) {
        val changes = event.getChanges(LibraryEntity::class.java).filterProjectLibraryChanges()
        if (changes.isEmpty()) return

        executeOrQueueOnDispatchThread {
          LOG.bracket("ProjectLibraryTable.EntityStoreChange") {
            for (change in changes) when (change) {
              is EntityChange.Added -> {
                val alreadyCreatedLibrary = event.storageAfter.libraryMap.getDataByEntity(change.entity) as LibraryBridgeImpl?
                val library = if (alreadyCreatedLibrary != null) {
                  alreadyCreatedLibrary.entityStorage = entityStorage
                  alreadyCreatedLibrary.modifiableModelFactory = null
                  alreadyCreatedLibrary
                }
                else {
                  var newLibrary: LibraryBridge? = null
                  WorkspaceModel.getInstance(project).updateProjectModelSilent {
                    newLibrary = it.mutableLibraryMap.getOrPutDataByEntity(change.entity) {
                      LibraryBridgeImpl(
                        libraryTable = this@ProjectLibraryTableBridgeImpl,
                        project = project,
                        initialId = change.entity.persistentId(),
                        initialEntityStorage = entityStorage,
                        targetBuilder = null
                      )
                    }
                  }
                  newLibrary!!
                }

                dispatcher.multicaster.afterLibraryAdded(library)
              }
              is EntityChange.Removed -> {
                val library = event.storageBefore.libraryMap.getDataByEntity(change.entity)

                if (library != null) {
                  // TODO There won't be any content in libraryImpl as EntityStore's current was already changed
                  dispatcher.multicaster.afterLibraryRemoved(library)
                  Disposer.dispose(library)
                }
              }
              is EntityChange.Replaced -> {
                val idBefore = change.oldEntity.persistentId()
                val idAfter = change.newEntity.persistentId()

                if (idBefore != idAfter) {
                  val library = event.storageBefore.libraryMap.getDataByEntity(change.oldEntity) as? LibraryBridgeImpl
                  if (library != null) {
                    library.entityId = idAfter
                    dispatcher.multicaster.afterLibraryRenamed(library, LibraryBridgeImpl.getLegacyLibraryName(idBefore))
                  }
                }
              }
            }
          }
        }
      }
    })

    executeOrQueueOnDispatchThread {
      val libraries = entityStorage.current
        .entities(LibraryEntity::class.java)
        .filter { it.tableId is LibraryTableId.ProjectLibraryTableId }
        .filter { entityStorage.current.libraryMap.getDataByEntity(it) == null }
        .map { libraryEntity ->
          Pair(libraryEntity, LibraryBridgeImpl(
            libraryTable = this@ProjectLibraryTableBridgeImpl,
            project = project,
            initialId = libraryEntity.persistentId(),
            initialEntityStorage = entityStorage,
            targetBuilder = null
          ))
        }
        .toList()
      if (libraries.isNotEmpty()) {
        runWriteAction {
          WorkspaceModel.getInstance(project).updateProjectModelSilent {
            libraries.forEach { (entity, library) ->
              it.mutableLibraryMap.addIfAbsent(entity, library)
            }
          }
        }
        libraries.forEach { (_, library) ->
          dispatcher.multicaster.afterLibraryAdded(library)
        }
      }
    }
  }

  override fun getProject(): Project = parentProject

  override fun getLibraries(): Array<Library> = entityStorage.cachedValue(libraryArrayValue)

  override fun createLibrary(): Library = createLibrary(null)

  override fun createLibrary(name: String?): Library {
    if (name == null) error("Creating unnamed project libraries is unsupported")

    if (getLibraryByName(name) != null) {
      error("Project library named $name already exists")
    }

    val modifiableModel = modifiableModel
    modifiableModel.createLibrary(name)
    modifiableModel.commit()

    val newLibrary = getLibraryByName(name)
    if (newLibrary == null) {
      error("Library $name was not created")
    }

    return newLibrary
  }

  override fun removeLibrary(library: Library) {
    val modifiableModel = modifiableModel
    modifiableModel.removeLibrary(library)
    modifiableModel.commit()
  }

  override fun getLibraryIterator(): Iterator<Library> = libraries.iterator()

  override fun getLibraryByName(name: String): Library? {
    val entity = entityStorage.current.resolve(LibraryId(name, LibraryTableId.ProjectLibraryTableId)) ?: return null
    return entityStorage.current.libraryMap.getDataByEntity(entity)
  }

  override fun getTableLevel(): String = LibraryTablesRegistrar.PROJECT_LEVEL
  override fun getPresentation(): LibraryTablePresentation = PROJECT_LIBRARY_TABLE_PRESENTATION

  override fun getModifiableModel(): LibraryTable.ModifiableModel =
    ProjectModifiableLibraryTableBridgeImpl(
      libraryTable = this,
      project = project,
      originalStorage = entityStorage.current
    )

  override fun getModifiableModel(diff: WorkspaceEntityStorageBuilder): LibraryTable.ModifiableModel =
    ProjectModifiableLibraryTableBridgeImpl(
      libraryTable = this,
      project = project,
      originalStorage = entityStorage.current,
      diff = diff
    )

  override fun addListener(listener: LibraryTable.Listener) = dispatcher.addListener(listener)
  override fun addListener(listener: LibraryTable.Listener, parentDisposable: Disposable) =
    dispatcher.addListener(listener, parentDisposable)

  override fun removeListener(listener: LibraryTable.Listener) = dispatcher.removeListener(listener)

  override fun dispose() {
    for (library in libraries) {
      Disposer.dispose(library)
    }
  }

  companion object {
    private fun List<EntityChange<LibraryEntity>>.filterProjectLibraryChanges() =
      filter {
        when (it) {
          is EntityChange.Added -> it.entity.tableId is LibraryTableId.ProjectLibraryTableId
          is EntityChange.Removed -> it.entity.tableId is LibraryTableId.ProjectLibraryTableId
          is EntityChange.Replaced -> it.oldEntity.tableId is LibraryTableId.ProjectLibraryTableId
        }
      }

    internal val PROJECT_LIBRARY_TABLE_PRESENTATION = object : LibraryTablePresentation() {
      override fun getDisplayName(plural: Boolean) = ProjectModelBundle.message("project.library.display.name", if (plural) 2 else 1)

      override fun getDescription() = ProjectModelBundle.message("libraries.node.text.project")

      override fun getLibraryTableEditorTitle() = ProjectModelBundle.message("library.configure.project.title")
    }

    private const val INDEX_ID = "libraryBridge"

    internal val WorkspaceEntityStorage.libraryMap: ExternalEntityMapping<LibraryBridge>
      get() = getExternalMapping(INDEX_ID)
    internal val WorkspaceEntityStorageDiffBuilder.mutableLibraryMap: MutableExternalEntityMapping<LibraryBridge>
      get() = getMutableExternalMapping(INDEX_ID)

    internal fun WorkspaceEntityStorage.findLibraryEntity(library: LibraryBridge) =
      libraryMap.getEntities(library).firstOrNull() as LibraryEntity?
  }
}
