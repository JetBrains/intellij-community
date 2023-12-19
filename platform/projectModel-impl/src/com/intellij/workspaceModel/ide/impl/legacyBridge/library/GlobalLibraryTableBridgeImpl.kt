// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.Disposable
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablePresentation
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.Disposer
import com.intellij.platform.diagnostic.telemetry.helpers.addMeasuredTimeMillis
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.projectModel.ProjectModelBundle
import com.intellij.workspaceModel.ide.impl.jpsMetrics
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import io.opentelemetry.api.metrics.Meter
import java.util.concurrent.atomic.AtomicLong

class GlobalLibraryTableBridgeImpl : GlobalLibraryTableBridge, Disposable {
  private val libraryTableDelegate = GlobalLibraryTableDelegate(this, LibraryTableId.GlobalLibraryTableId(LibraryTablesRegistrar.APPLICATION_LEVEL))

  override fun initializeBridges(changes: Map<Class<*>, List<EntityChange<*>>>,
                                        builder: MutableEntityStorage) = initializeLibraryBridgesTimeMs.addMeasuredTimeMillis {
    libraryTableDelegate.initializeLibraryBridges(changes, builder)
  }

  override fun initializeBridgesAfterLoading(mutableStorage: MutableEntityStorage,
                                                    initialEntityStorage: VersionedEntityStorage): () -> Unit
    = initializeLibraryBridgesAfterLoadingTimeMs.addMeasuredTimeMillis {
    return@addMeasuredTimeMillis libraryTableDelegate.initializeLibraryBridgesAfterLoading(mutableStorage, initialEntityStorage)
  }

  override fun handleBeforeChangeEvents(event: VersionedStorageChange) = handleBeforeChangeEventsTimeMs.addMeasuredTimeMillis {
    libraryTableDelegate.handleBeforeChangeEvents(event)
  }

  override fun handleChangedEvents(event: VersionedStorageChange) = handleChangedEventsTimeMs.addMeasuredTimeMillis {
    libraryTableDelegate.handleChangedEvents(event)
  }

  override fun getLibraries(): Array<Library> = getLibrariesTimeMs.addMeasuredTimeMillis {
    return@addMeasuredTimeMillis libraryTableDelegate.getLibraries()
  }

  override fun getLibraryIterator(): Iterator<Library> = libraries.iterator()

  override fun getLibraryByName(name: String): Library? = getLibraryByNameTimeMs.addMeasuredTimeMillis {
    return@addMeasuredTimeMillis libraryTableDelegate.getLibraryByName(name)
  }

  override fun createLibrary(): Library = createLibrary(null)

  override fun createLibrary(name: String?): Library = createLibraryTimeMs.addMeasuredTimeMillis {
    return@addMeasuredTimeMillis libraryTableDelegate.createLibrary(name)
  }

  override fun removeLibrary(library: Library): Unit = libraryTableDelegate.removeLibrary(library)

  override fun getTableLevel(): String = LibraryTablesRegistrar.APPLICATION_LEVEL

  override fun getPresentation(): LibraryTablePresentation = GLOBAL_LIBRARY_TABLE_PRESENTATION

  override fun getModifiableModel(): LibraryTable.ModifiableModel = GlobalOrCustomModifiableLibraryTableBridgeImpl(this)

  override fun dispose(): Unit = Disposer.dispose(libraryTableDelegate)

  override fun addListener(listener: LibraryTable.Listener) = libraryTableDelegate.addListener(listener)

  override fun addListener(listener: LibraryTable.Listener, parentDisposable: Disposable): Unit = libraryTableDelegate.addListener(listener, parentDisposable)

  override fun removeListener(listener: LibraryTable.Listener) = libraryTableDelegate.removeListener(listener)

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

    internal val initializeLibraryBridgesTimeMs: AtomicLong = AtomicLong()
    private val initializeLibraryBridgesAfterLoadingTimeMs: AtomicLong = AtomicLong()
    private val handleBeforeChangeEventsTimeMs: AtomicLong = AtomicLong()
    private val handleChangedEventsTimeMs: AtomicLong = AtomicLong()
    private val getLibrariesTimeMs: AtomicLong = AtomicLong()
    private val createLibraryTimeMs: AtomicLong = AtomicLong()
    private val getLibraryByNameTimeMs: AtomicLong = AtomicLong()

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
          initializeLibraryBridgesTimeCounter.record(initializeLibraryBridgesTimeMs.get())
          initializeLibraryBridgesAfterLoadingTimeCounter.record(initializeLibraryBridgesAfterLoadingTimeMs.get())
          handleBeforeChangeEventsTimeCounter.record(handleBeforeChangeEventsTimeMs.get())
          handleChangedEventsTimeCounter.record(handleChangedEventsTimeMs.get())
          getLibrariesTimeCounter.record(getLibrariesTimeMs.get())
          createLibraryTimeCounter.record(createLibraryTimeMs.get())
          getLibraryByNameTimeCounter.record(getLibraryByNameTimeMs.get())
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
