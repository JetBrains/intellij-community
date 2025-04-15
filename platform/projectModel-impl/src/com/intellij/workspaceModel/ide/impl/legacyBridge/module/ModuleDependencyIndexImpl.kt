// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointPriorityListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.libraries.CustomLibraryTableDescription
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.serialization.impl.LibraryNameGenerator
import com.intellij.platform.workspace.storage.EntityChange
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.ide.impl.jpsMetrics
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridge
import com.intellij.workspaceModel.ide.legacyBridge.LibraryModifiableModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableRootModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyListener
import io.opentelemetry.api.metrics.Meter
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

@ApiStatus.Internal
open class ModuleDependencyIndexImpl(private val project: Project): ModuleDependencyIndex, Disposable {
  companion object {
    @JvmStatic
    private val LOG = logger<ModuleDependencyIndexImpl>()

    private val changedListenerTimeMs = MillisecondsMeasurer()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val changedListenerTimeCounter = meter.counterBuilder("jps.module.dependency.index.workspace.model.listener.on.changed.ms").buildObserver()

      meter.batchCallback(
        {
          changedListenerTimeCounter.record(changedListenerTimeMs.asMilliseconds())
        }, changedListenerTimeCounter
      )
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }

  private val eventDispatcher = EventDispatcher.create(ModuleDependencyListener::class.java)
  
  private val libraryTablesListener = LibraryTablesListener()
  private val jdkChangeListener = JdkChangeListener()
  private val rootSetChangeListener = ReferencedRootSetChangeListener()
  
  init {
    if (!project.isDefault) {
      val messageBusConnection = project.messageBus.connect(this)
      messageBusConnection.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, jdkChangeListener)
    }

    CustomLibraryTableDescription.CUSTOM_TABLES_EP.point.addExtensionPointListener(
      // We need a priority listener here because it's important to unsubscribe before the custom table will be removed at
      // [LibraryTablesRegistrarImpl.getCustomLibrariesMap]
      object : ExtensionPointListener<CustomLibraryTableDescription>, ExtensionPointPriorityListener {
        override fun extensionRemoved(extension: CustomLibraryTableDescription, pluginDescriptor: PluginDescriptor) {
          LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(extension.tableLevel, project)?.let { table ->
            libraryTablesListener.unsubscribeFromCustomTableOnDispose(table)
          }
        }
      }, true, project)
  }

  override fun addListener(listener: ModuleDependencyListener) {
    eventDispatcher.addListener(listener)
  }

  override fun removeListener(listener: ModuleDependencyListener) {
    eventDispatcher.removeListener(listener)
  }

  override fun setupTrackedLibrariesAndJdks() {
    ThreadingAssertions.assertWriteAccess()
    LOG.debug { "Add tracked global libraries and SDK for all modules" }
    val currentStorage = WorkspaceModel.getInstance(project).currentSnapshot
    val addedLibsCollector = mutableSetOf<LibraryId>()
    val addedLibraryLevelsCollector = MultiSet<String>()
    for (moduleEntity in currentStorage.entities(ModuleEntity::class.java)) {
      collectAddedLibrariesAndAddSdks(moduleEntity.dependencies, moduleEntity, addedLibsCollector, addedLibraryLevelsCollector)
    }
    addedLibsCollector.forEach { addTrackedLibrary(it, null) }
    trackLibraryLevel(addedLibraryLevelsCollector)
  }

  override fun hasProjectSdkDependency(): Boolean {
    return jdkChangeListener.hasProjectSdkDependency()
  }

  override fun hasDependencyOn(libraryId: LibraryId): Boolean {
    return libraryId.tableId is LibraryTableId.ModuleLibraryTableId || libraryTablesListener.hasDependencyOn(libraryId)
  }

  override fun hasDependencyOn(library: Library): Boolean {
    return library.table == null || libraryTablesListener.hasDependencyOn(library)
  }

  override fun hasDependencyOn(sdk: Sdk): Boolean {
    return jdkChangeListener.hasDependencyOn(sdk)
  }

  override fun hasDependencyOn(sdk: SdkId): Boolean {
    return jdkChangeListener.hasDependencyOn(sdk)
  }

  fun workspaceModelChanged(event: VersionedStorageChange) = changedListenerTimeMs.addMeasuredTime {
    if (project.isDisposed) return

    // By using set we make sure that we won't add the same library multiple times
    val newLibrariesCollector = mutableSetOf<LibraryId>()
    val newLibraryLevels = MultiSet<String>()
    val removedLibrariesCollector = mutableSetOf<LibraryId>()
    val removeLibraryLevels = MultiSet<String>()
    // Roots changed event should be fired for the global libraries linked with module
    val moduleChanges = event.getChanges(ModuleEntity::class.java)
    for (change in moduleChanges) {
      when (change) {
        is EntityChange.Added -> {
          LOG.debug { "Add tracked global libraries and SDK from ${change.newEntity.name}" }
          collectAddedLibrariesAndAddSdks(change.newEntity.dependencies, change.newEntity, newLibrariesCollector, newLibraryLevels)
        }
        is EntityChange.Removed -> {
          LOG.debug { "Removed tracked global libraries and SDK from ${change.oldEntity.name}" }
          collectRemovedLibrariesAndRemoveSdks(change.oldEntity.dependencies, change.oldEntity, removedLibrariesCollector, removeLibraryLevels)
        }
        is EntityChange.Replaced -> {
          val removedDependencies = change.oldEntity.dependencies - change.newEntity.dependencies.toSet()
          val addedDependencies = change.newEntity.dependencies - change.oldEntity.dependencies.toSet()
          LOG.debug { "Update tracked global libraries and SDK for ${change.newEntity.name}: ${removedDependencies.size} removed, ${addedDependencies.size} added" }
          collectRemovedLibrariesAndRemoveSdks(removedDependencies, change.oldEntity, removedLibrariesCollector, removeLibraryLevels)
          collectAddedLibrariesAndAddSdks(addedDependencies, change.newEntity, newLibrariesCollector, newLibraryLevels)
        }
      }
    }

    removedLibrariesCollector.forEach { unTrackLibrary(it, event.storageAfter) }
    newLibrariesCollector.forEach { addTrackedLibrary(it, event.storageBefore) }
    untrackLibraryLevel(removeLibraryLevels)
    trackLibraryLevel(newLibraryLevels)
  }

  private fun collectAddedLibrariesAndAddSdks(dependencies: List<ModuleDependencyItem>,
                                              moduleEntity: ModuleEntity,
                                              libraryIdsCollector: MutableSet<LibraryId>,
                                              libraryLevelsCollector: MultiSet<String>) {
    dependencies.forEach {
      when {
        it is LibraryDependency && it.library.tableId !is LibraryTableId.ModuleLibraryTableId -> {
          libraryIdsCollector.add(it.library)
          libraryLevelsCollector.add(it.library.tableId.level)
        }
        it is SdkDependency || it is InheritedSdkDependency -> {
          jdkChangeListener.addTrackedJdk(it, moduleEntity)
        }
      }
    }
  }

  private fun collectRemovedLibrariesAndRemoveSdks(dependencies: List<ModuleDependencyItem>,
                                                   moduleEntity: ModuleEntity,
                                                   removeLibrariesCollector: MutableSet<LibraryId>,
                                                   removeLibraryLevelsCollector: MultiSet<String>) {
    dependencies.forEach {
      when {
        it is LibraryDependency && it.library.tableId !is LibraryTableId.ModuleLibraryTableId -> {
          removeLibrariesCollector.add(it.library)
          removeLibraryLevelsCollector.add(it.library.tableId.level)
        }
        it is SdkDependency || it is InheritedSdkDependency -> {
          jdkChangeListener.removeTrackedJdk(it, moduleEntity)
        }
      }
    }
  }

  override fun dispose() {
    if (project.isDefault) return

    //there is no need to send events since the project will be disposed anyway
    libraryTablesListener.unsubscribe(false)
    jdkChangeListener.unsubscribe(false)
  }

  private inner class ReferencedRootSetChangeListener : RootProvider.RootSetChangedListener {
    override fun rootSetChanged(wrapper: RootProvider) {
      if (wrapper is Library) {
        eventDispatcher.multicaster.referencedLibraryChanged(wrapper)
      }
      else {
        val value = if (wrapper is Sdk) {
          wrapper
        } else {
          require(wrapper is Supplier<*>) { "Unexpected root provider $wrapper does not implement Supplier<Sdk>" }
          wrapper.get()
        }
        require(value is Sdk) { "Unexpected root provider $wrapper does not implement Supplier<Sdk>" }
        eventDispatcher.multicaster.referencedSdkChanged(value)
      }
    }
  }

  private fun addTrackedLibrary(libraryId: LibraryId, storageBefore: ImmutableEntityStorage?) {
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    val libraryLevel = libraryId.tableId.level
    val libraryTable = libraryTablesRegistrar.getLibraryTableByLevel(libraryLevel, project) ?: return
    if (storageBefore == null || storageBefore.referrers(libraryId, ModuleEntity::class.java).none()) {
      val library = libraryTable.getLibraryByName(libraryId.name)
      if (library != null) {
        library.rootProvider.addRootSetChangedListener(rootSetChangeListener)
        eventDispatcher.multicaster.addedDependencyOn(library)
      }
    }
  }

  private fun trackLibraryLevel(newLibraryLevels: MultiSet<String>) {
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    newLibraryLevels.forEachWithOccurrences { libraryLevel, occ ->
      val libraryTable = libraryTablesRegistrar.getLibraryTableByLevel(libraryLevel, project) ?: return@forEachWithOccurrences
      if (LibraryLevelsTracker.getInstance(project).isNotUsed(libraryLevel)) libraryTable.addListener(libraryTablesListener)
      LibraryLevelsTracker.getInstance(project).dependencyWithLibraryLevelAdded(libraryTable.tableLevel, occ)
    }
  }

  private fun unTrackLibrary(libraryId: LibraryId, currentStorage: ImmutableEntityStorage) {
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    val libraryLevel = libraryId.tableId.level
    val libraryTable = libraryTablesRegistrar.getLibraryTableByLevel(libraryLevel, project) ?: return
    val library = libraryTable.getLibraryByName(libraryId.name)
    if (currentStorage.referrers(libraryId, ModuleEntity::class.java).none() && library != null) {
      eventDispatcher.multicaster.removedDependencyOn(library)
      library.rootProvider.removeRootSetChangedListener(rootSetChangeListener)
    }
  }

  private fun untrackLibraryLevel(removedLibraryLevels: MultiSet<String>) {
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    removedLibraryLevels.forEachWithOccurrences { libraryLevel, occ ->
      val libraryTable = libraryTablesRegistrar.getLibraryTableByLevel(libraryLevel, project) ?: return@forEachWithOccurrences
      LibraryLevelsTracker.getInstance(project).dependencyWithLibraryLevelRemoved(libraryLevel, occ)
      if (LibraryLevelsTracker.getInstance(project).isNotUsed(libraryLevel)) libraryTable.removeListener(libraryTablesListener)
    }
  }

  private inner class LibraryTablesListener : LibraryTable.Listener {
    override fun afterLibraryAdded(newLibrary: Library) {
      if (hasDependencyOn(newLibrary)) {
        eventDispatcher.multicaster.referencedLibraryAdded(newLibrary)
        eventDispatcher.multicaster.addedDependencyOn(newLibrary)
        newLibrary.rootProvider.addRootSetChangedListener(rootSetChangeListener)
      }
    }

    override fun afterLibraryRemoved(library: Library) {
      if (hasDependencyOn(library)) {
        library.rootProvider.removeRootSetChangedListener(rootSetChangeListener)
        eventDispatcher.multicaster.removedDependencyOn(library)
        eventDispatcher.multicaster.referencedLibraryRemoved(library)
      }
    }

    fun hasDependencyOn(library: Library): Boolean {
      return when (library) {
        is LibraryBridge -> hasDependencyOn(library.libraryId)
        is LibraryModifiableModelBridge -> hasDependencyOn(library.libraryId)
        else -> error("Unexpected type of library ${library::class.java}: $library")
      }
    }

    fun hasDependencyOn(libraryId: LibraryId) = project.workspaceModel.currentSnapshot.referrers(libraryId, ModuleEntity::class.java).any()

    override fun afterLibraryRenamed(library: Library, oldName: String?) {
      ThreadingAssertions.assertWriteAccess()

      val libraryTable = library.table
      val newName = library.name
      if (libraryTable != null && oldName != null && newName != null) {
        val libraryTableId = LibraryNameGenerator.getLibraryTableId(libraryTable.tableLevel)
        val libraryId = LibraryId(oldName, libraryTableId)

        // We are allowed to get all modules and then update the project model because we are in a write action
        //   However, if the write action has to be removed from here, this all has to be done in `WorkspaceModel.update` for consistency
        val affectedModules = project.workspaceModel.currentSnapshot.referrers(libraryId, ModuleEntity::class.java)

        if (affectedModules.any()) {
          WorkspaceModel.getInstance(project).updateProjectModel("Module dependency index: after library renamed") { builder ->
            //maybe it makes sense to simplify this code by reusing code from PEntityStorageBuilder.updateSoftReferences
            affectedModules.forEach { module ->
              val updated = module.dependencies.map {
                when {
                  it is LibraryDependency && it.library.tableId == libraryTableId && it.library.name == oldName ->
                    it.copy(library = LibraryId(newName, libraryTableId))
                  else -> it
                }
              } as MutableList<ModuleDependencyItem>
              builder.modifyModuleEntity(module) {
                dependencies = updated
              }
            }
          }
        }
      }
    }

    fun unsubscribe(fireEvents: Boolean) {
      val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()

      // getInstanceIfInitialized is used here as there is nothing to unsubscribe from if the tracker was not used.
      // Also, this `unsubscribe` function is called from dispose and this is not allowed to initialize services during dispose
      val libraryTracker = LibraryLevelsTracker.getInstanceIfInitialized(project)
      libraryTracker?.getLibraryLevels()?.forEach { libraryLevel ->
        val libraryTable = libraryTablesRegistrar.getLibraryTableByLevel(libraryLevel, project)
        libraryTable?.libraryIterator?.forEach {
          if (fireEvents && hasDependencyOn(it)) {
            eventDispatcher.multicaster.removedDependencyOn(it)
          }
          it.rootProvider.removeRootSetChangedListener(rootSetChangeListener)
        }
        libraryTable?.removeListener(libraryTablesListener)
      }
      if (fireEvents) {
        libraryTablesRegistrar.libraryTable.libraryIterator.forEach {
          if (hasDependencyOn(it)) {
            eventDispatcher.multicaster.removedDependencyOn(it)
          }
        }
      }
      libraryTracker?.clear()
    }

    fun unsubscribeFromCustomTableOnDispose(libraryTable: LibraryTable) {
      LibraryLevelsTracker.getInstance(project).getLibraryLevels().forEach { libraryLevel ->
        if (libraryTable.tableLevel != libraryLevel) return@forEach
        libraryTable.libraryIterator.forEach {
          it.rootProvider.removeRootSetChangedListener(rootSetChangeListener)
        }
        libraryTable.removeListener(libraryTablesListener)
      }
    }
  }

  private inner class JdkChangeListener : ProjectJdkTable.Listener, ProjectRootManagerEx.ProjectJdkListener {
    private val sdkDependencies = MultiMap.createSet<ModuleDependencyItem, ModuleId>()
    private val watchedSdks = HashSet<Sdk>()
    private var watchedProjectSdk: Sdk? = null
    private var projectJdkListenerAdded = false

    override fun jdkAdded(jdk: Sdk) {
      if (hasDependencyOn(jdk)) {
        if (watchedSdks.isEmpty()) {
          eventDispatcher.multicaster.firstDependencyOnSdkAdded()
        }
        eventDispatcher.multicaster.referencedSdkAdded(jdk)
        if (hasProjectSdkDependency() && isProjectSdk(jdk)) {
          watchedProjectSdk = jdk
        }
        if (watchedSdks.add(jdk)) {
          eventDispatcher.multicaster.addedDependencyOn(jdk)
          jdk.rootProvider.addRootSetChangedListener(rootSetChangeListener)
        }
      }
    }

    override fun jdkNameChanged(jdk: Sdk, previousName: String) {
      val sdkDependency = SdkDependency(SdkId(previousName, jdk.sdkType.name))
      val affectedModules = sdkDependencies.get(sdkDependency)
      if (affectedModules.isNotEmpty()) {
        WorkspaceModel.getInstance(project).updateProjectModel("Module dependency index: jdk name changed") { builder ->
          for (moduleId in affectedModules) {
            val module = moduleId.resolve(builder) ?: continue
            val updated = module.dependencies.map {
              when (it) {
                is SdkDependency -> SdkDependency(SdkId(jdk.name, jdk.sdkType.name))
                else -> it
              }
            } as MutableList<ModuleDependencyItem>
            builder.modifyModuleEntity(module) {
              dependencies = updated
            }
          }
        }
      }
    }

    override fun jdkRemoved(jdk: Sdk) {
      if (hasProjectSdkDependency() && isProjectSdk(jdk)) {
        watchedProjectSdk = null
      }
      if (watchedSdks.remove(jdk)) {
        jdk.rootProvider.removeRootSetChangedListener(rootSetChangeListener)
        eventDispatcher.multicaster.removedDependencyOn(jdk)
      }
      if (hasDependencyOn(jdk)) {
        eventDispatcher.multicaster.referencedSdkRemoved(jdk)
      }
      if (watchedSdks.isEmpty()) {
        eventDispatcher.multicaster.lastDependencyOnSdkRemoved()
      }
    }

    fun addTrackedJdk(sdkDependency: ModuleDependencyItem, moduleEntity: ModuleEntity) {
      if (sdkDependency == InheritedSdkDependency && !projectJdkListenerAdded) {
        (projectRootManager as ProjectRootManagerEx).addProjectJdkListener(this)
        projectJdkListenerAdded = true
      }
      val sdk = findSdk(sdkDependency)
      if (sdk != null) {
        if (sdkDependency == InheritedSdkDependency) {
          watchedProjectSdk = sdk
        }
        addTrackedJdk(sdk)
      }
      sdkDependencies.putValue(sdkDependency, moduleEntity.symbolicId)
    }

    private fun addTrackedJdk(sdk: Sdk) {
      if (watchedSdks.isEmpty()) {
        eventDispatcher.multicaster.firstDependencyOnSdkAdded()
      }
      if (watchedSdks.add(sdk)) {
        eventDispatcher.multicaster.addedDependencyOn(sdk)
        sdk.rootProvider.addRootSetChangedListener(rootSetChangeListener)
      }
    }

    fun removeTrackedJdk(sdkDependency: ModuleDependencyItem, moduleEntity: ModuleEntity) {
      sdkDependencies.remove(sdkDependency, moduleEntity.symbolicId)
      val sdk = findSdk(sdkDependency)
      if (sdkDependency == InheritedSdkDependency && !hasProjectSdkDependency()) {
        watchedProjectSdk = null
      }
      if (sdk != null) {
        removeTrackedJdk(sdk)
      }
    }

    private fun removeTrackedJdk(sdk: Sdk) {
      if (!hasDependencyOn(sdk) && watchedSdks.remove(sdk)) {
        sdk.rootProvider.removeRootSetChangedListener(rootSetChangeListener)
        eventDispatcher.multicaster.removedDependencyOn(sdk)
        if (watchedSdks.isEmpty()) {
          eventDispatcher.multicaster.lastDependencyOnSdkRemoved()
        }
      }
    }

    override fun projectJdkChanged() {
      if (hasProjectSdkDependency()) {
        watchedProjectSdk?.let { removeTrackedJdk(it) }
        watchedProjectSdk = projectRootManager.projectSdk
        watchedProjectSdk?.let { addTrackedJdk(it) }
      }
    }

    fun hasProjectSdkDependency(): Boolean {
      return sdkDependencies.get(InheritedSdkDependency).isNotEmpty()
    }

    private val projectRootManager by lazy { ProjectRootManager.getInstance(project) }

    private fun findSdk(sdkDependency: ModuleDependencyItem): Sdk? = when (sdkDependency) {
      is InheritedSdkDependency -> projectRootManager.projectSdk
      is SdkDependency -> ModifiableRootModelBridge.findSdk(sdkDependency.sdk.name, sdkDependency.sdk.type)
      else -> null
    }

    fun hasDependencyOn(jdk: Sdk): Boolean {
      return hasDependencyOn(SdkId(jdk.name, jdk.sdkType.name))
    }

    fun hasDependencyOn(sdk: SdkId): Boolean {
      return sdkDependencies.get(SdkDependency(sdk)).isNotEmpty()
             || isProjectSdk(sdk) && (hasProjectSdkDependency() || watchedSdks.isEmpty())
    }

    private fun isProjectSdk(jdk: Sdk) = isProjectSdk(jdk.name, jdk.sdkType.name)

    private fun isProjectSdk(sdkId: SdkId) = isProjectSdk(sdkId.name, sdkId.type)

    private fun isProjectSdk(sdkName: String, sdkType: String) =
      sdkName == projectRootManager.projectSdkName && sdkType == projectRootManager.projectSdkTypeName

    fun unsubscribe(fireEvents: Boolean) {
      watchedSdks.forEach { sdk ->
        if (fireEvents) {
          if (hasDependencyOn(sdk)) {
            eventDispatcher.multicaster.removedDependencyOn(sdk)
          }
        }
        sdk.rootProvider.removeRootSetChangedListener(rootSetChangeListener)
      }
      watchedSdks.clear()
    }
  }

  override fun reset() {
    libraryTablesListener.unsubscribe(true)
    jdkChangeListener.unsubscribe(true)
    setupTrackedLibrariesAndJdks()
  }
}