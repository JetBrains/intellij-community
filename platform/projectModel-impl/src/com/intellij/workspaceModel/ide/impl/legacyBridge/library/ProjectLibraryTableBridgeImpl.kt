// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablePresentation
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.platform.backend.workspace.BridgeInitializer
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.WorkspaceModelTopics
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.serialization.impl.LibraryNameGenerator
import com.intellij.platform.workspace.storage.*
import com.intellij.projectModel.ProjectModelBundle
import com.intellij.util.EventDispatcher
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap
import com.intellij.workspaceModel.ide.legacyBridge.ProjectLibraryTableBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

private class ProjectLibraryTableBridgeInitializer : BridgeInitializer {
  override fun isEnabled(): Boolean = true

  override fun initializeBridges(project: Project, changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    @Suppress("UNCHECKED_CAST")
    val libraryChanges = (changes[LibraryEntity::class.java] as? List<EntityChange<LibraryEntity>>) ?: emptyList()
    val addChanges = libraryChanges.filterProjectLibraryChanges().filterIsInstance<EntityChange.Added<LibraryEntity>>()

    for (addChange in addChanges) {
      // Will initialize the bridge if missing
      builder.mutableLibraryMap.getOrPutDataByEntity(addChange.newEntity) {
        LibraryBridgeImpl(
          libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project),
          origin = LibraryOrigin.OfProject(project),
          initialId = addChange.newEntity.symbolicId,
          initialEntityStorage = (WorkspaceModel.getInstance(project) as WorkspaceModelInternal).entityStorage,
          targetBuilder = builder,
        )
      }
    }
  }
}

@ApiStatus.Internal
open class ProjectLibraryTableBridgeImpl(
  private val parentProject: Project
) : ProjectLibraryTableBridge, Disposable {

  private val entityStorage: VersionedEntityStorage = (WorkspaceModel.getInstance(parentProject) as WorkspaceModelInternal).entityStorage

  private val dispatcher = EventDispatcher.create(LibraryTable.Listener::class.java)

  private val libraryArrayValue = CachedValue<Array<Library>> { storage ->
    storage.entities(LibraryEntity::class.java).filter { it.tableId == LibraryTableId.ProjectLibraryTableId }
      .mapNotNull { storage.libraryMap.getDataByEntity(it) }
      .toList().toTypedArray()
  }

  init {
    project.messageBus.connect(this).subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      /**
       * This is a flag indicating that the [beforeChanged] method was called. Due to the fact that we subscribe using the code, this
       *   may lead to IDEA-324532.
       * With this flag we skip the "after" event if the before event wasn't called.
       */
      private var beforeCalled = false

      override fun beforeChanged(event: VersionedStorageChange) {
        beforeCalled = true
        val libraryChanges = event.getChanges(LibraryEntity::class.java)
        val removeChanges = libraryChanges.filterProjectLibraryChanges().filterIsInstance<EntityChange.Removed<LibraryEntity>>()
        if (removeChanges.isEmpty()) return

        for (change in removeChanges) {
          val library = event.storageBefore.libraryMap.getDataByEntity(change.oldEntity)
          LOG.debug { "Fire 'beforeLibraryRemoved' event for ${change.oldEntity.name}, library = $library" }
          if (library != null) {
            dispatcher.multicaster.beforeLibraryRemoved(library)
          }
        }
      }

      override fun changed(event: VersionedStorageChange) {
        if (!beforeCalled) return
        beforeCalled = false
        val changes = event.getChanges(LibraryEntity::class.java).filterProjectLibraryChanges()
        if (changes.isEmpty()) return

        for (change in changes) {
          LOG.debug { "Process library change $change" }
          when (change) {
            is EntityChange.Added -> {
              val alreadyCreatedLibrary = event.storageAfter.libraryMap.getDataByEntity(change.newEntity) as? LibraryBridgeImpl
                                          ?: error("Library bridge should be created in `before` method")
              alreadyCreatedLibrary.entityStorage = entityStorage
              alreadyCreatedLibrary.clearTargetBuilder()

              dispatcher.multicaster.afterLibraryAdded(alreadyCreatedLibrary)
            }
            is EntityChange.Removed -> {
              val library = event.storageBefore.libraryMap.getDataByEntity(change.oldEntity)

              if (library != null) {
                // TODO There won't be any content in libraryImpl as EntityStore's current was already changed
                dispatcher.multicaster.afterLibraryRemoved(library)
                Disposer.dispose(library)
              }
            }
            is EntityChange.Replaced -> {
              val idBefore = change.oldEntity.symbolicId
              val idAfter = change.newEntity.symbolicId

              if (idBefore != idAfter) {
                val library = event.storageBefore.libraryMap.getDataByEntity(change.oldEntity) as? LibraryBridgeImpl
                if (library != null) {
                  library.entityId = idAfter
                  dispatcher.multicaster.afterLibraryRenamed(library, LibraryNameGenerator.getLegacyLibraryName(idBefore))
                }
              }
            }
          }
        }
      }
    })
  }

  suspend fun loadLibraries(targetBuilder: MutableEntityStorage?) {
    val storage = targetBuilder ?: entityStorage.current
    val libraries = storage
      .entities(LibraryEntity::class.java)
      .filter { it.tableId is LibraryTableId.ProjectLibraryTableId }
      .filter { storage.libraryMap.getDataByEntity(it) == null }
      .map { libraryEntity ->
        Pair(libraryEntity, LibraryBridgeImpl(
          libraryTable = this@ProjectLibraryTableBridgeImpl,
          origin = LibraryOrigin.OfProject(project),
          initialId = libraryEntity.symbolicId,
          initialEntityStorage = entityStorage,
          targetBuilder = targetBuilder
        ))
      }
      .toList()
    LOG.debug("Initial load of project-level libraries")
    if (libraries.isEmpty()) {
      return
    }

    if (targetBuilder == null) {
      withContext(Dispatchers.EDT) {
        (project.serviceAsync<WorkspaceModel>() as WorkspaceModelImpl).updateProjectModelSilent("Add project library mapping") {
          for ((entity, library) in libraries) {
            it.mutableLibraryMap.addIfAbsent(entity, library)
          }
        }
        edtWriteAction {
          for ((_, library) in libraries) {
            dispatcher.multicaster.afterLibraryAdded(library)
          }
        }
      }
    }
    else {
      for ((entity, library) in libraries) {
        targetBuilder.mutableLibraryMap.addIfAbsent(entity, library)
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

  override fun getModifiableModel(diff: MutableEntityStorage): LibraryTable.ModifiableModel =
    ProjectModifiableLibraryTableBridgeImpl(
      libraryTable = this,
      project = project,
      originalStorage = entityStorage.current,
      diff = diff,
      cacheStorageResult = false
    )

  override fun addListener(listener: LibraryTable.Listener) {
    dispatcher.addListener(listener)
  }

  override fun addListener(listener: LibraryTable.Listener, parentDisposable: Disposable) {
    dispatcher.addListener(listener, parentDisposable)
  }

  override fun removeListener(listener: LibraryTable.Listener) {
    dispatcher.removeListener(listener)
  }

  override fun dispose() {
    for (library in libraries) {
      Disposer.dispose(library)
    }
  }

  companion object {
    internal val PROJECT_LIBRARY_TABLE_PRESENTATION = object : LibraryTablePresentation() {
      override fun getDisplayName(plural: Boolean) = ProjectModelBundle.message("project.library.display.name", if (plural) 2 else 1)

      override fun getDescription() = ProjectModelBundle.message("libraries.node.text.project")

      override fun getLibraryTableEditorTitle() = ProjectModelBundle.message("library.configure.project.title")
    }

    private val LIBRARY_BRIDGE_MAPPING_ID = ExternalMappingKey.create<LibraryBridge>("intellij.libraries.bridge")

    val EntityStorage.libraryMap: ExternalEntityMapping<LibraryBridge>
      get() = getExternalMapping(LIBRARY_BRIDGE_MAPPING_ID)
    val MutableEntityStorage.mutableLibraryMap: MutableExternalEntityMapping<LibraryBridge>
      get() = getMutableExternalMapping(LIBRARY_BRIDGE_MAPPING_ID)

    fun EntityStorage.findLibraryEntity(library: LibraryBridge): LibraryEntity? {
      return libraryMap.getEntities(library).firstOrNull() as LibraryEntity?
    }

    private val LOG = logger<ProjectLibraryTableBridgeImpl>()
  }
}

private fun List<EntityChange<LibraryEntity>>.filterProjectLibraryChanges(): List<EntityChange<LibraryEntity>> {
  return filter {
    when (it) {
      is EntityChange.Added -> it.newEntity.tableId is LibraryTableId.ProjectLibraryTableId
      is EntityChange.Removed -> it.oldEntity.tableId is LibraryTableId.ProjectLibraryTableId
      is EntityChange.Replaced -> it.oldEntity.tableId is LibraryTableId.ProjectLibraryTableId
    }
  }
}

