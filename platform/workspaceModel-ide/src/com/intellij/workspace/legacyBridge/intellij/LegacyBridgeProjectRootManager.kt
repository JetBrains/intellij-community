package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.InheritedJdkOrderEntry
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.RootProvider.RootSetChangedListener
import com.intellij.openapi.roots.impl.ProjectRootManagerComponent
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar.APPLICATION_LEVEL
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.util.containers.BidirectionalMultiMap
import com.intellij.workspace.api.*
import com.intellij.workspace.bracket
import com.intellij.workspace.ide.WorkspaceModelChangeListener
import com.intellij.workspace.ide.WorkspaceModelTopics

@Suppress("ComponentNotRegistered")
class LegacyBridgeProjectRootManager(project: Project) : ProjectRootManagerComponent(project) {
  private val LOG = Logger.getInstance(javaClass)
  private val libraryNameDelimiter = ":"
  private val globalLibraryTableListener = GlobalLibraryTableListener()
  private val rootProviderPerModule = mutableMapOf<RootProvider, MutableSet<ModuleEntity>>()

  init {
    val bus = project.messageBus.connect(this)

    WorkspaceModelTopics.getInstance(project).subscribeAfterModuleLoading(bus, object : WorkspaceModelChangeListener {
      override fun beforeChanged(event: EntityStoreChanged) {
        if (myProject.isDisposed || Disposer.isDisposing(myProject)) return

        val performUpdate = processChanges(event, project)

        if (performUpdate) myRootsChanged.beforeRootsChanged()
      }

      override fun changed(event: EntityStoreChanged) {
        if (myProject.isDisposed || Disposer.isDisposing(myProject)) return
        LOG.bracket("ProjectRootManager.EntityStoreChange") {

          val performUpdate = processChanges(event, project)
          if (performUpdate) myRootsChanged.rootsChanged()

          // Roots changed even should be fired for the global libraries linked with module
          val moduleChanges = event.getChanges(ModuleEntity::class.java)
          for (change in moduleChanges) {
            when (change) {
              is EntityChange.Added -> addTrackedLibraryFromEntity(change.entity)
              is EntityChange.Removed -> unTrackLibraryFromEntity(change.entity)
              is EntityChange.Replaced -> {
                unTrackLibraryFromEntity(change.oldEntity)
                addTrackedLibraryFromEntity(change.newEntity)
              }
            }
          }
        }
      }
    })

    val listener = RootSetChangedListener {
      makeRootsChange(EmptyRunnable.INSTANCE, false, true)
    }

    // TODO It's also possible not to fire roots change event if JDK is not used
    bus.subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, object : ProjectJdkTable.Listener {
      override fun jdkAdded(jdk: Sdk) {
        jdk.rootProvider.addRootSetChangedListener(listener, this@LegacyBridgeProjectRootManager)
      }

      override fun jdkNameChanged(jdk: Sdk, previousName: String) {
      }

      override fun jdkRemoved(jdk: Sdk) {
        jdk.rootProvider.removeRootSetChangedListener(listener)
      }
    })
  }

  override fun getActionToRunWhenProjectJdkChanges(): Runnable {
    return Runnable { if (hasModuleWithInheritedJdk()) makeRootsChange(EmptyRunnable.INSTANCE, false, true) }
  }

  override fun projectClosed() {
    super.projectClosed()
    unsubscribeListeners()
  }

  override fun dispose() {
    super.dispose()
    unsubscribeListeners()
  }

  private fun unsubscribeListeners() {
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    val globalLibraryTable = libraryTablesRegistrar.libraryTable
    globalLibraryTableListener.getLibraryLevels().forEach {
      when (it) {
        APPLICATION_LEVEL -> globalLibraryTable.removeListener(globalLibraryTableListener)
        else -> libraryTablesRegistrar.getLibraryTableByLevel(it, project)?.removeListener(globalLibraryTableListener)
      }
    }
    globalLibraryTableListener.clear()
    rootProviderPerModule.keys.forEach { it.removeRootSetChangedListener(globalLibraryTableListener) }
    rootProviderPerModule.clear()
  }

  // Library changes should not fire any events if the library is not included in any of order entries
  private fun processChanges(events: EntityStoreChanged, project: Project): Boolean {
    val libraryChanges = events.getChanges(LibraryEntity::class.java)
    return if (libraryChanges.isNotEmpty() && libraryChanges.count() == events.getAllChanges().count()) {
      for (event in libraryChanges) {
        val res = when (event) {
          is EntityChange.Added -> libraryHasOrderEntry(event.entity.name, project)
          is EntityChange.Removed -> libraryHasOrderEntry(event.entity.name, project)
          is EntityChange.Replaced -> libraryHasOrderEntry(event.newEntity.name, project)
        }
        if (res) return true
      }
      return false
    } else true
  }

  private fun addTrackedLibraryFromEntity(moduleEntity: ModuleEntity) {
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    val globalLibraryTable = libraryTablesRegistrar.libraryTable
    moduleEntity.dependencies.filterIsInstance<ModuleDependencyItem.Exportable.LibraryDependency>()
      .filter { it.library.tableId is LibraryTableId.GlobalLibraryTableId }
      .forEach {
        val libraryName = it.library.name
        val libraryLevel = it.library.tableId.level
        if (globalLibraryTableListener.isEmpty(libraryLevel)) {
          when (libraryLevel) {
            APPLICATION_LEVEL -> globalLibraryTable.addListener(globalLibraryTableListener)
            else -> libraryTablesRegistrar.getLibraryTableByLevel(libraryLevel, project)?.addListener(
              globalLibraryTableListener)
          }
        }
        globalLibraryTableListener.addTrackedLibrary(moduleEntity, "$libraryLevel$libraryNameDelimiter$libraryName")

        val library = globalLibraryTable.getLibraryByName(libraryName)
        if (library !is RootProvider) return@forEach
        if (!rootProviderPerModule.contains(library)) library.addRootSetChangedListener(globalLibraryTableListener)
        rootProviderPerModule.computeIfAbsent(library) { mutableSetOf() }.add(moduleEntity)
      }
  }

  private fun unTrackLibraryFromEntity(moduleEntity: ModuleEntity) {
    val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
    val globalLibraryTable = libraryTablesRegistrar.libraryTable
    moduleEntity.dependencies.filterIsInstance<ModuleDependencyItem.Exportable.LibraryDependency>()
      .filter { it.library.tableId is LibraryTableId.GlobalLibraryTableId }
      .forEach {
        val libraryName = it.library.name
        val libraryLevel = it.library.tableId.level
        globalLibraryTableListener.let { listener ->
          listener.unTrackLibrary(moduleEntity, "$libraryLevel$libraryNameDelimiter$libraryName")
          if (listener.isEmpty(libraryLevel)) {
            when (libraryName) {
              APPLICATION_LEVEL -> globalLibraryTable.removeListener(listener)
              else -> libraryTablesRegistrar.getLibraryTableByLevel(libraryLevel, project)?.removeListener(listener)
            }
          }
        }

        val library = globalLibraryTable.getLibraryByName(libraryName)
        if (library !is RootProvider) return@forEach
        rootProviderPerModule[library]?.let { modules ->
          modules.remove(moduleEntity)
          if (modules.isEmpty()) {
            library.removeRootSetChangedListener(globalLibraryTableListener)
            rootProviderPerModule.remove(library)
          }
        }
      }
  }

  private fun hasModuleWithInheritedJdk() = ModuleManager.getInstance(project).modules.asSequence()
    .filter { ModuleRootManager.getInstance(it).orderEntries.filterIsInstance<InheritedJdkOrderEntry>().any() }.any()

  private fun libraryHasOrderEntry(name: String, project: Project): Boolean {
    ModuleManager.getInstance(project).modules.forEach { module ->
      val exists = ModuleRootManager.getInstance(module).orderEntries.any { it is LibraryOrderEntry && it.libraryName == name }
      if (exists) return true
    }
    return false
  }

  // Listener for global libraries linked to module
  private inner class GlobalLibraryTableListener : LibraryTable.Listener, RootSetChangedListener {
    private val librariesPerModuleMap = BidirectionalMultiMap<ModuleEntity, String>()
    private var insideRootsChange = false

    fun addTrackedLibrary(moduleEntity: ModuleEntity, libraryIdentifier: String) {
      librariesPerModuleMap.put(moduleEntity, libraryIdentifier)
    }

    fun unTrackLibrary(moduleEntity: ModuleEntity, libraryIdentifier: String) {
      librariesPerModuleMap.remove(moduleEntity, libraryIdentifier)
    }

    fun isEmpty(libraryLevel: String) = librariesPerModuleMap.values.asSequence().filter { it.startsWith("$libraryLevel$libraryNameDelimiter") }.none()

    fun getLibraryLevels() = librariesPerModuleMap.values.asSequence().map { it.split(libraryNameDelimiter)[0] }.toSet()

    override fun afterLibraryAdded(newLibrary: Library) {
      if (librariesPerModuleMap.containsValue(getLibraryIdentifier(newLibrary))) makeRootsChange(EmptyRunnable.INSTANCE, false, true)
    }

    override fun afterLibraryRemoved(library: Library) {
      if (librariesPerModuleMap.containsValue(getLibraryIdentifier(library))) makeRootsChange(EmptyRunnable.INSTANCE, false, true)
    }

    override fun rootSetChanged(wrapper: RootProvider) {
      if (insideRootsChange) return
      insideRootsChange = true
      try {
        makeRootsChange(EmptyRunnable.INSTANCE, false, true)
      }
      finally {
        insideRootsChange = false
      }
    }

    private fun getLibraryIdentifier(library: Library) = "${library.table.tableLevel}$libraryNameDelimiter${library.name}"

    internal fun clear() = librariesPerModuleMap.clear()
  }
}
