// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablePresentation
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.eel.EelMachine
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.serialization.impl.JpsGlobalEntitiesSerializers
import com.intellij.platform.workspace.storage.*
import com.intellij.projectModel.ProjectModelBundle
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.jpsMetrics
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import io.opentelemetry.api.metrics.Meter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class GlobalLibraryTableBridgeImpl(val eelMachine: EelMachine) : GlobalLibraryTableBridge, Disposable {
  private val libraryTableDelegate = GlobalLibraryTableDelegate(this, eelMachine, LibraryTableId.GlobalLibraryTableId(LibraryTablesRegistrar.APPLICATION_LEVEL))

  override fun initializeBridges(changes: Map<Class<*>, List<EntityChange<*>>>,
                                        builder: MutableEntityStorage) = initializeLibraryBridgesTimeMs.addMeasuredTime {
    libraryTableDelegate.initializeLibraryBridges(changes, builder)
  }

  override fun initializeBridgesAfterLoading(mutableStorage: MutableEntityStorage,
                                                    initialEntityStorage: VersionedEntityStorage): () -> Unit
    = initializeLibraryBridgesAfterLoadingTimeMs.addMeasuredTime {
    return@addMeasuredTime libraryTableDelegate.initializeLibraryBridgesAfterLoading(mutableStorage, initialEntityStorage)
  }

  override fun handleBeforeChangeEvents(event: VersionedStorageChange) = handleBeforeChangeEventsTimeMs.addMeasuredTime {
    libraryTableDelegate.handleBeforeChangeEvents(event)
  }

  override fun handleChangedEvents(event: VersionedStorageChange) = handleChangedEventsTimeMs.addMeasuredTime {
    libraryTableDelegate.handleChangedEvents(event)
  }

  override fun getLibraries(): Array<Library> = getLibrariesTimeMs.addMeasuredTime {
    return@addMeasuredTime libraryTableDelegate.getLibraries()
  }

  override fun getLibraryIterator(): Iterator<Library> = libraries.iterator()

  override fun getLibraryByName(name: String): Library? = getLibraryByNameTimeMs.addMeasuredTime {
    return@addMeasuredTime libraryTableDelegate.getLibraryByName(name)
  }

  override fun createLibrary(): Library = createLibrary(null)

  override fun createLibrary(name: String?): Library = createLibraryTimeMs.addMeasuredTime {
    return@addMeasuredTime libraryTableDelegate.createLibrary(name)
  }

  override fun removeLibrary(library: Library): Unit = libraryTableDelegate.removeLibrary(library)

  override fun getTableLevel(): String = LibraryTablesRegistrar.APPLICATION_LEVEL

  override fun getPresentation(): LibraryTablePresentation = GLOBAL_LIBRARY_TABLE_PRESENTATION

  override fun getModifiableModel(): LibraryTable.ModifiableModel {
    return GlobalOrCustomModifiableLibraryTableBridgeImpl(this, eelMachine, createEntitySourceForGlobalLibrary())
  }

  override fun dispose(): Unit = Disposer.dispose(libraryTableDelegate)

  override fun addListener(listener: LibraryTable.Listener) = libraryTableDelegate.addListener(listener)

  override fun addListener(listener: LibraryTable.Listener, parentDisposable: Disposable): Unit = libraryTableDelegate.addListener(listener, parentDisposable)

  override fun removeListener(listener: LibraryTable.Listener) = libraryTableDelegate.removeListener(listener)

  private fun createEntitySourceForGlobalLibrary(): EntitySource {
    val virtualFileUrlManager = GlobalWorkspaceModel.getInstance(eelMachine).getVirtualFileUrlManager()
    val globalLibrariesFile = virtualFileUrlManager.getOrCreateFromUrl(PathManager.getOptionsFile(JpsGlobalEntitiesSerializers.GLOBAL_LIBRARIES_FILE_NAME).absolutePath)
    return JpsGlobalFileEntitySource(globalLibrariesFile)
  }

  companion object {
    private val GLOBAL_LIBRARY_TABLE_PRESENTATION: LibraryTablePresentation = object : LibraryTablePresentation() {
      override fun getDisplayName(plural: Boolean): String {
        return ProjectModelBundle.message("global.library.display.name", if (plural) 2 else 1)
      }

      override fun getDescription(): String {
        return ProjectModelBundle.message("libraries.node.text.ide")
      }

      override fun getLibraryTableEditorTitle(): String {
        return ProjectModelBundle.message("library.configure.global.title")
      }
    }

    internal val initializeLibraryBridgesTimeMs = MillisecondsMeasurer()
    private val initializeLibraryBridgesAfterLoadingTimeMs = MillisecondsMeasurer()
    private val handleBeforeChangeEventsTimeMs = MillisecondsMeasurer()
    private val handleChangedEventsTimeMs = MillisecondsMeasurer()
    private val getLibrariesTimeMs = MillisecondsMeasurer()
    private val createLibraryTimeMs = MillisecondsMeasurer()
    private val getLibraryByNameTimeMs = MillisecondsMeasurer()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val initializeLibraryBridgesTimeCounter = meter.counterBuilder("jps.global.initialize.library.bridges.ms").buildObserver()
      val initializeLibraryBridgesAfterLoadingTimeCounter = meter.counterBuilder("jps.global.initialize.library.bridges.after.loading.ms").buildObserver()
      val handleBeforeChangeEventsTimeCounter = meter.counterBuilder("jps.global.handle.before.change.events.ms").buildObserver()
      val handleChangedEventsTimeCounter = meter.counterBuilder("jps.global.handle.changed.events.ms").buildObserver()
      val getLibrariesTimeCounter = meter.counterBuilder("jps.global.get.libraries.ms").buildObserver()
      val createLibraryTimeCounter = meter.counterBuilder("jps.global.get.library.ms").buildObserver()
      val getLibraryByNameTimeCounter = meter.counterBuilder("jps.global.get.library.by.name.ms").buildObserver()

      meter.batchCallback(
        {
          initializeLibraryBridgesTimeCounter.record(initializeLibraryBridgesTimeMs.asMilliseconds())
          initializeLibraryBridgesAfterLoadingTimeCounter.record(initializeLibraryBridgesAfterLoadingTimeMs.asMilliseconds())
          handleBeforeChangeEventsTimeCounter.record(handleBeforeChangeEventsTimeMs.asMilliseconds())
          handleChangedEventsTimeCounter.record(handleChangedEventsTimeMs.asMilliseconds())
          getLibrariesTimeCounter.record(getLibrariesTimeMs.asMilliseconds())
          createLibraryTimeCounter.record(createLibraryTimeMs.asMilliseconds())
          getLibraryByNameTimeCounter.record(getLibraryByNameTimeMs.asMilliseconds())
        },
        initializeLibraryBridgesTimeCounter, initializeLibraryBridgesAfterLoadingTimeCounter, handleBeforeChangeEventsTimeCounter,
        handleChangedEventsTimeCounter, getLibrariesTimeCounter, createLibraryTimeCounter, getLibraryByNameTimeCounter
      )
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }
}
