package com.intellij.workspace.legacyBridge.libraries.libraries

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablePresentation
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.ConcurrentMultiMap
import com.intellij.workspace.api.*
import com.intellij.workspace.bracket
import com.intellij.workspace.executeOrQueueOnDispatchThread
import com.intellij.workspace.ide.WorkspaceModel
import com.intellij.workspace.ide.WorkspaceModelChangeListener
import com.intellij.workspace.ide.WorkspaceModelTopics
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class LegacyBridgeProjectLibraryTableImpl(
  private val parentProject: Project
) : ProjectLibraryTable, Disposable {

  private val LOG = Logger.getInstance(javaClass)

  private val librariesMap: ConcurrentMap<LibraryId, LegacyBridgeLibraryImpl> = ConcurrentHashMap()
  private val libraryNameMap: ConcurrentMultiMap<String, LegacyBridgeLibraryImpl> = ConcurrentMultiMap()

  private val newLibraryInstances = mutableMapOf<LibraryId, LegacyBridgeLibraryImpl>()

  private val entityStore: TypedEntityStore = WorkspaceModel.getInstance(parentProject).entityStore

  @ApiStatus.Internal
  internal fun setNewLibraryInstances(addedInstances: List<LegacyBridgeLibraryImpl>) {
    if (newLibraryInstances.isNotEmpty()) error("setNewLibraryInstances are not empty")
    for (instance in addedInstances) {
      newLibraryInstances[instance.libraryId] = instance
    }
  }

  private fun removeLibraryFromMaps(id: LibraryId, libraryImpl: LegacyBridgeLibraryImpl) {
    librariesMap.remove(id)

    val namesToRemove = mutableListOf<String>()
    for (entry in libraryNameMap.entrySet()) {
      if (entry.value.contains(libraryImpl)) {
        namesToRemove.add(entry.key)
      }
    }

    for (name in namesToRemove) {
      libraryNameMap.remove(name, libraryImpl)
    }
  }

  private fun addLibraryToMaps(library: LegacyBridgeLibraryImpl) {
    val entityId = library.entityId

    val existingLibrary = librariesMap.put(entityId, library)
    if (existingLibrary != null) {
      error("Library with $entityId was already exist")
    }

    libraryNameMap.putValue(entityId.name, library)
  }

  init {
    val messageBusConnection = project.messageBus.connect(this)

    messageBusConnection.subscribe(WorkspaceModelTopics.CHANGED, object : WorkspaceModelChangeListener {
      override fun beforeChanged(event: EntityStoreChanged) {
        val changes = event.getChanges(LibraryEntity::class.java).filterProjectLibraryChanges()
          .filterIsInstance<EntityChange.Removed<LibraryEntity>>()
        if (changes.isEmpty()) return

        executeOrQueueOnDispatchThread {
          LOG.bracket("ProjectLibraryTable.beforeChanged") {
            for (change in changes) {
              val libraryImpl = librariesMap.getValue(change.entity.persistentId())
              dispatcher.multicaster.beforeLibraryRemoved(libraryImpl)
            }
          }
        }
      }

      override fun changed(event: EntityStoreChanged) {
        val changes = event.getChanges(LibraryEntity::class.java).filterProjectLibraryChanges()
        if (changes.isEmpty()) return

        executeOrQueueOnDispatchThread {
          LOG.bracket("ProjectLibraryTable.EntityStoreChange") {
            for (change in changes) when (change) {
              is EntityChange.Added -> {
                val addedLibraryId = change.entity.persistentId()
                val alreadyCreatedLibrary = newLibraryInstances.remove(addedLibraryId)
                val libraryImpl = if (alreadyCreatedLibrary != null) {
                  alreadyCreatedLibrary.entityStore = entityStore
                  alreadyCreatedLibrary.modifiableModelFactory = null
                  alreadyCreatedLibrary
                }
                else LegacyBridgeLibraryImpl(
                  libraryTable = this@LegacyBridgeProjectLibraryTableImpl,
                  project = project,
                  initialEntityStore = entityStore,
                  initialId = addedLibraryId,
                  initialModifiableModelFactory = null,
                  parent = this@LegacyBridgeProjectLibraryTableImpl
                )

                addLibraryToMaps(libraryImpl)

                dispatcher.multicaster.afterLibraryAdded(libraryImpl)
              }
              is EntityChange.Removed -> {
                val removedLibraryId = change.entity.persistentId()
                val libraryImpl = librariesMap.getValue(removedLibraryId)

                removeLibraryFromMaps(removedLibraryId, libraryImpl)

                // TODO There won't be any content in libraryImpl as EntityStore's current was already changed
                dispatcher.multicaster.afterLibraryRemoved(libraryImpl)

                Disposer.dispose(libraryImpl)
              }
              is EntityChange.Replaced -> {
                val idBefore = change.oldEntity.persistentId()
                val idAfter = change.newEntity.persistentId()

                if (idBefore != idAfter) {
                  val library = librariesMap.getValue(idBefore)
                  removeLibraryFromMaps(idBefore, library)

                  library.entityId = idAfter
                  addLibraryToMaps(library)

                  dispatcher.multicaster.afterLibraryRenamed(library)
                }
              }
            }
          }

          if (newLibraryInstances.isNotEmpty()) {
            LOG.error("Not all library instances were handled in change event. Leftovers:\n" +
                      newLibraryInstances.keys.joinToString(separator = "\n"))
            newLibraryInstances.clear()
          }
        }
      }
    })
  }

  override fun getProject(): Project = parentProject

  override fun getLibraries(): Array<Library> = librariesMap.values.toTypedArray()

  override fun createLibrary(): Library = createLibrary(null)

  override fun createLibrary(name: String?): Library {
    if (name == null) error("Creating unnamed project libraries is unsupported")

    if (getLibraryByName(name) != null) {
      error("Library named $name already exists")
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

  override fun getLibraryIterator(): MutableIterator<Library> = librariesMap.values.toMutableList().iterator()

  override fun getLibraryByName(name: String): Library? = libraryNameMap[name].firstOrNull()

  override fun getTableLevel(): String = LibraryTablesRegistrar.PROJECT_LEVEL
  override fun getPresentation(): LibraryTablePresentation = PROJECT_LIBRARY_TABLE_PRESENTATION

  override fun getModifiableModel(): LibraryTable.ModifiableModel =
    LegacyBridgeProjectModifiableLibraryTableImpl(
      libraryTable = this,
      project = project,
      originalStorage = entityStore.current
    )

  private val dispatcher = EventDispatcher.create(LibraryTable.Listener::class.java)
  override fun addListener(listener: LibraryTable.Listener) = dispatcher.addListener(listener)
  override fun addListener(listener: LibraryTable.Listener, parentDisposable: Disposable) =
    dispatcher.addListener(listener, parentDisposable)

  override fun removeListener(listener: LibraryTable.Listener) = dispatcher.removeListener(listener)

  override fun dispose() = Unit

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
      override fun getDisplayName(plural: Boolean) = ProjectBundle.message("project.library.display.name", if (plural) 2 else 1)

      override fun getDescription() = ProjectBundle.message("libraries.node.text.project")

      override fun getLibraryTableEditorTitle() = ProjectBundle.message("library.configure.project.title")
    }
  }
}
