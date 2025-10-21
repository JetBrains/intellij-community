// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.libraries.CustomLibraryTable
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablePresentation
import com.intellij.openapi.util.Disposer
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.workspace.jps.GlobalStorageEntitySource
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.serialization.impl.JpsLibraryEntitiesSerializer
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageOnBuilder
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.mutableLibraryMap
import com.intellij.workspaceModel.ide.legacyBridge.CustomLibraryTableBridge
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer
import org.jetbrains.jps.model.serialization.module.JpsModuleRootModelSerializer

internal class CustomLibraryTableBridgeImpl(private val level: String, private val presentation: LibraryTablePresentation)
  : CustomLibraryTableBridge, CustomLibraryTable, Disposable {
  private var tmpEntityStorage: EntityStorage? = null
  private val entitySource = LegacyCustomLibraryEntitySource(tableLevel)
  private val libraryTableId = LibraryTableId.GlobalLibraryTableId(tableLevel)
  private val libraryTableDelegate = GlobalLibraryTableDelegate(this, getMachine(), libraryTableId)

  override fun initializeBridgesAfterLoading(mutableStorage: MutableEntityStorage,
                                                    initialEntityStorage: VersionedEntityStorage): () -> Unit {
    return libraryTableDelegate.initializeLibraryBridgesAfterLoading(mutableStorage, initialEntityStorage)
  }

  override fun initializeBridges(changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    libraryTableDelegate.initializeLibraryBridges(changes, builder)
  }

  override fun handleBeforeChangeEvents(event: VersionedStorageChange) {
    libraryTableDelegate.handleBeforeChangeEvents(event)
  }

  override fun handleChangedEvents(event: VersionedStorageChange) {
    libraryTableDelegate.handleChangedEvents(event)
  }

  override fun getLibraries(): Array<Library> {
    return tmpEntityStorage?.entities(LibraryEntity::class.java)
             ?.filter { it.tableId == libraryTableId }
             ?.mapNotNull { tmpEntityStorage?.libraryMap?.getDataByEntity(it) }
             ?.toList()
             ?.toTypedArray()
           ?: libraryTableDelegate.getLibraries()
  }

  override fun getLibraryIterator(): Iterator<Library> = getLibraries().iterator()

  override fun getLibraryByName(name: String): Library? {
    return tmpEntityStorage?.resolve(LibraryId(name, libraryTableId))?.let { entity ->
      tmpEntityStorage?.libraryMap?.getDataByEntity(entity)
    } ?: libraryTableDelegate.getLibraryByName(name)
  }

  override fun createLibrary(): Library = createLibrary(null)

  override fun createLibrary(name: String?): Library = libraryTableDelegate.createLibrary(name)

  override fun removeLibrary(library: Library): Unit = libraryTableDelegate.removeLibrary(library)

  override fun getTableLevel(): String = level

  override fun getPresentation(): LibraryTablePresentation = presentation

  override fun getModifiableModel(): LibraryTable.ModifiableModel {
    return GlobalOrCustomModifiableLibraryTableBridgeImpl(this, getMachine(), entitySource)
  }

  override fun isEditable(): Boolean = false

  override fun dispose() {
    if (libraries.isEmpty()) return
    Disposer.dispose(libraryTableDelegate)

    val runnable: () -> Unit = {
      GlobalWorkspaceModel.getInstance(getMachine()).updateModel("Cleanup custom libraries after dispose") { storage ->
        storage.entities(LibraryEntity::class.java).filter { it.entitySource == entitySource }.forEach {
          storage.removeEntity(it)
        }
      }
    }

    val application = ApplicationManager.getApplication()
    if (application.isWriteAccessAllowed) {
      runnable.invoke()
    }
    else {
      application.invokeLater {
        application.runWriteAction {
          runnable.invoke()
        }
      }
    }
  }

  @OptIn(EntityStorageInstrumentationApi::class)
  override fun readExternal(libraryTableTag: Element) {
    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance(getMachine())
    val mutableEntityStorage = MutableEntityStorage.create()

    libraryTableTag.getChildren(JpsLibraryTableSerializer.LIBRARY_TAG).forEach { libraryTag ->
      val name = libraryTag.getAttributeValue(JpsModuleRootModelSerializer.NAME_ATTRIBUTE)
      val libraryEntity = JpsLibraryEntitiesSerializer.loadLibrary(name, libraryTag, libraryTableId, entitySource,
                                                                   globalWorkspaceModel.getVirtualFileUrlManager())
      mutableEntityStorage.addEntity(libraryEntity)
    }

    if (!(mutableEntityStorage as MutableEntityStorageInstrumentation).hasChanges()) return

    // Based on the assumption that no one changes the application library file manually, we can reuse existing bridges
    val storageOnBuilder = VersionedEntityStorageOnBuilder(mutableEntityStorage)
    mutableEntityStorage.entities(LibraryEntity::class.java).forEach { libraryEntity ->
      val globalSnapshot = globalWorkspaceModel.currentSnapshot
      val originalLibrary = globalSnapshot.resolve(libraryEntity.symbolicId)
      val (actualLibraryEntity, entityStorage) = if (originalLibrary != null) {
        originalLibrary to globalSnapshot
      }
      else {
        libraryEntity to mutableEntityStorage
      }

      val libraryBridge = actualLibraryEntity.findLibraryBridge(entityStorage) ?: LibraryBridgeImpl(
        libraryTable = this,
        origin = LibraryOrigin.OfMachine(getMachine()),
        initialId = libraryEntity.symbolicId,
        initialEntityStorage = storageOnBuilder,
        targetBuilder = null
      )
      mutableEntityStorage.mutableLibraryMap.addMapping(libraryEntity, libraryBridge as LibraryBridge)
    }

    assert(tmpEntityStorage == null) { "Temporary storage has to be null. Possibly, it's the second call of the method" }
    tmpEntityStorage = mutableEntityStorage

    val runnable: () -> Unit = {
      globalWorkspaceModel.updateModel("Custom library table ${libraryTableId.level} update") { builder ->
        builder.replaceBySource({ it == entitySource }, mutableEntityStorage)
      }
      tmpEntityStorage = null
    }

    val application = ApplicationManager.getApplication()
    if (application.isWriteAccessAllowed) {
      runnable.invoke()
    }
    else {
      application.invokeLater {
        application.runWriteAction {
          runnable.invoke()
        }
      }
    }
  }

  override fun writeExternal(element: Element) {
    GlobalWorkspaceModel.getInstance(LocalEelMachine).currentSnapshot.entities(LibraryEntity::class.java)
      .filter { it.tableId == libraryTableId }
      .sortedBy { it.name }
      .forEach { libraryEntity ->
        val libraryTag = JpsLibraryEntitiesSerializer.saveLibrary(libraryEntity, null, false)
        element.addContent(libraryTag)
      }
  }

  override fun addListener(listener: LibraryTable.Listener) = libraryTableDelegate.addListener(listener)

  override fun addListener(listener: LibraryTable.Listener, parentDisposable: Disposable): Unit = libraryTableDelegate.addListener(listener, parentDisposable)

  override fun removeListener(listener: LibraryTable.Listener) = libraryTableDelegate.removeListener(listener)
}

/**
 * As for now, we permit custom library tables only for local projects. These tables are internal anyway.
 */
private fun getMachine(): EelMachine {
  return LocalEelMachine
}

@ApiStatus.Internal
data class LegacyCustomLibraryEntitySource(private val levelId: String): GlobalStorageEntitySource