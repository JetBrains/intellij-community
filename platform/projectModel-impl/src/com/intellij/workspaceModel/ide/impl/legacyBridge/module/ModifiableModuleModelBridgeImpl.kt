// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleWithNameAlreadyExists
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.addElapsedTimeMs
import com.intellij.platform.workspace.jps.JpsMetrics
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.jps.serialization.impl.ModulePath
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.projectModel.ProjectModelBundle
import com.intellij.util.PathUtil
import com.intellij.util.containers.BidirectionalMap
import com.intellij.util.io.systemIndependentPath
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.LegacyBridgeJpsEntitySourceFactory
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeModifiableBase
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerBridgeImpl.Companion.mutableModuleMap
import com.intellij.workspaceModel.ide.legacyBridge.ModifiableModuleModelBridge
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import io.opentelemetry.api.metrics.Meter
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

private val LOG: Logger
  get() = logger<ModifiableModuleModelBridgeImpl>()

internal class ModifiableModuleModelBridgeImpl(
  private val project: Project,
  private val moduleManager: ModuleManagerBridgeImpl,
  diff: MutableEntityStorage,
  cacheStorageResult: Boolean = true
) : LegacyBridgeModifiableBase(diff, cacheStorageResult), ModifiableModuleModelBridge {
  override fun getProject(): Project = project

  private val modulesToAdd = BidirectionalMap<String, ModuleBridge>()
  private val modulesToDispose = HashMap<String, ModuleBridge>()
  private val uncommittedModulesToDispose = ArrayList<ModuleBridge>()
  private val currentModuleSet = moduleManager.modules.toMutableSet()
  private val newNameToModule = BidirectionalMap<String, ModuleBridge>()
  private var moduleGroupsAreModified = false

  // TODO Add cache?
  override fun getModules(): Array<Module> = currentModuleSet.toTypedArray()

  override fun newNonPersistentModule(moduleName: String, moduleTypeId: String): Module {
    val moduleEntity = diff addEntity ModuleEntity(name = moduleName,
                                                   dependencies = listOf(ModuleDependencyItem.ModuleSourceDependency),
                                                   entitySource = NonPersistentEntitySource
    )

    val module = moduleManager.createModule(moduleEntity.symbolicId, moduleName, null, entityStorageOnDiff, diff)
    diff.mutableModuleMap.addMapping(moduleEntity, module)
    modulesToAdd.put(moduleName, module)
    currentModuleSet.add(module)

    module.init(null)
    module.setModuleType(moduleTypeId)
    return module
  }

  override fun newModule(filePath: String, moduleTypeId: String): Module {
    val start = System.currentTimeMillis()

    // TODO Handle filePath, add correct iml source with a path

    // TODO Must be in sync with module loading. It is not now
    val canonicalPath = FileUtil.toSystemIndependentName(resolveShortWindowsName(filePath))

    val existingModule = getModuleByFilePath(canonicalPath)
    if (existingModule != null) {
      return existingModule
    }

    val moduleName = ModulePath.getModuleNameByFilePath(canonicalPath)
    if (findModuleByName(moduleName) != null) {
      throw ModuleWithNameAlreadyExists("Module already exists: $moduleName", moduleName)
    }

    val entitySource = LegacyBridgeJpsEntitySourceFactory.createEntitySourceForModule(
      project = project,
      baseModuleDir = VirtualFileUrlManager.getInstance(project).fromPath(PathUtil.getParentPath(canonicalPath)),
      externalSource = null,
    )

    val moduleEntity = diff addEntity ModuleEntity(name = moduleName,
                                                   dependencies = listOf(ModuleDependencyItem.ModuleSourceDependency),
                                                   entitySource = entitySource
    ) {
      type = moduleTypeId
    }

    val moduleInstance = createModuleInstance(moduleEntity, true)
    newModuleTimeMs.addElapsedTimeMs(start)
    return moduleInstance
  }

  private fun resolveShortWindowsName(filePath: String): String {
    return try {
      FileUtil.resolveShortWindowsName(filePath)
    }
    catch (ignored: IOException) {
      filePath
    }
  }

  private fun createModuleInstance(moduleEntity: ModuleEntity, isNew: Boolean): ModuleBridge {
    val plugins = PluginManagerCore.getPluginSet().getEnabledModules()
    val moduleInstance = moduleManager.createModuleInstance(moduleEntity = moduleEntity,
                                                            versionedStorage = entityStorageOnDiff,
                                                            diff = diff,
                                                            isNew = isNew,
                                                            precomputedExtensionModel = null,
                                                            plugins = plugins,
                                                            corePlugin = plugins.firstOrNull { it.pluginId == PluginManagerCore.CORE_ID })
    diff.mutableModuleMap.addMapping(moduleEntity, moduleInstance)
    modulesToAdd[moduleEntity.name] = moduleInstance
    currentModuleSet.add(moduleInstance)
    return moduleInstance
  }

  private fun getModuleByFilePath(filePath: String): ModuleBridge? {
    for (module in modules) {
      val sameFilePath = when (SystemInfo.isFileSystemCaseSensitive) {
        true -> module.moduleFilePath == filePath
        false -> module.moduleFilePath.equals(filePath, ignoreCase = true)
      }

      if (sameFilePath) {
        return module as ModuleBridge
      }
    }

    return null
  }

  override fun loadModule(file: Path) = loadModule(file.systemIndependentPath)

  override fun loadModule(filePath: String): Module {
    val start = System.currentTimeMillis()

    val moduleName = ModulePath.getModuleNameByFilePath(filePath)
    if (findModuleByName(moduleName) != null) {
      error("Module name '$moduleName' already exists. Trying to load module: $filePath")
    }

    val moduleEntity = moduleManager.loadModuleToBuilder(moduleName, filePath, diff)
    val moduleInstance = createModuleInstance(moduleEntity, false)

    loadModuleTimeMs.addElapsedTimeMs(start)
    return moduleInstance
  }

  override fun disposeModule(module: Module) {
    if (module.project.isDisposed()) {
      //if the project is being disposed now, removing module won't work because WorkspaceModelImpl won't fire events and the module won't be disposed
      //it looks like this may happen in tests only so it's ok to skip removal of the module since the project will be disposed anyway
      return
    }

    module as ModuleBridge

    if (findModuleByName(module.name) == null) {
      LOG.error("Module '${module.name}' is not found. Probably it's already disposed.")
      return
    }

    if (modulesToAdd.containsValue(module)) {
      modulesToAdd.removeValue(module)
      uncommittedModulesToDispose.add(module)
    }
    currentModuleSet.remove(module)

    newNameToModule.removeValue(module)
    modulesToDispose[module.name] = module
    val moduleEntity = module.findModuleEntity(diff)
    if (moduleEntity == null) {
      LOG.error("Could not find module entity to remove by $module")
      return
    }
    moduleEntity.dependencies
      .asSequence()
      .filterIsInstance<ModuleDependencyItem.Exportable.LibraryDependency>()
      .filter { (it.library.tableId as? LibraryTableId.ModuleLibraryTableId)?.moduleId == module.moduleEntityId }
      .mapNotNull { it.library.resolve(diff) }
      .forEach {
        diff.removeEntity(it)
      }
    diff.removeEntity(moduleEntity)
  }

  override fun findModuleByName(name: String): Module? {
    val addedModule = modulesToAdd[name]
    if (addedModule != null) return addedModule

    if (modulesToDispose.containsKey(name)) return null

    val newNameModule = newNameToModule[name]
    if (newNameModule != null) return null

    return moduleManager.findModuleByName(name)
  }

  override fun dispose() {
    val start = System.currentTimeMillis()

    assertModelIsLive()

    ApplicationManager.getApplication().assertWriteAccessAllowed()

    for (moduleToAdd in modulesToAdd.values) {
      Disposer.dispose(moduleToAdd)
    }
    for (module in uncommittedModulesToDispose) {
      Disposer.dispose(module)
    }

    modulesToAdd.clear()
    modulesToDispose.clear()
    newNameToModule.clear()

    disposingTimeMs.addElapsedTimeMs(start)
  }

  override fun isChanged(): Boolean =
    modulesToAdd.isNotEmpty() ||
    modulesToDispose.isNotEmpty() ||
    newNameToModule.isNotEmpty() ||
    moduleGroupsAreModified

  override fun commit() {
    val diff = collectChanges()

    WorkspaceModel.getInstance(project).updateProjectModel("Module model commit") {
      it.addDiff(diff)
    }
  }

  override fun prepareForCommit() {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    uncommittedModulesToDispose.forEach { module -> Disposer.dispose(module) }
  }

  override fun collectChanges(): MutableEntityStorage {
    prepareForCommit()
    return diff
  }

  override fun renameModule(module: Module, newName: String) {
    val start = System.currentTimeMillis()

    module as ModuleBridge

    val oldModule = findModuleByName(newName)

    val uncommittedOldName = newNameToModule.getKeysByValue(module)
    newNameToModule.removeValue(module)
    newNameToModule.remove(newName)

    val oldName = uncommittedOldName ?: module.name
    if (oldName != newName) { // if renaming to itself, forget it altogether
      val moduleToAdd = modulesToAdd.remove(oldName)
      if (moduleToAdd != null) {
        moduleToAdd.rename(newName, true)
        modulesToAdd[newName] = moduleToAdd
      }
      else {
        newNameToModule[newName] = module
      }
      val entity = module.findModuleEntity(entityStorageOnDiff.current) ?: error("Unable to find module entity for $module")
      diff.modifyEntity(entity) {
        name = newName
      }
    }

    if (oldModule != null) {
      throw ModuleWithNameAlreadyExists(ProjectModelBundle.message("module.already.exists.error", newName), newName)
    }

    moduleRenamingTimeMs.addElapsedTimeMs(start)
  }

  override fun getModuleToBeRenamed(newName: String): Module? = newNameToModule[newName]
  override fun getNewName(module: Module): String? = newNameToModule.getKeysByValue(module as ModuleBridge)?.single()
  override fun getActualName(module: Module): String = getNewName(module) ?: module.name

  override fun getModuleGroupPath(module: Module): Array<String>? =
    ModuleManagerBridgeImpl.getModuleGroupPath(module, entityStorageOnDiff)

  override fun hasModuleGroups(): Boolean = ModuleManagerBridgeImpl.hasModuleGroups(entityStorageOnDiff)

  override fun setModuleGroupPath(module: Module, groupPath: Array<out String>?) {
    val storage = entityStorageOnDiff.current
    val moduleEntity = (module as ModuleBridge).findModuleEntity(storage) ?: error("Could not resolve module entity for $module")
    val moduleGroupEntity = moduleEntity.groupPath
    val groupPathList = groupPath?.toMutableList()

    // TODO How to deduplicate with ModuleCustomImlDataEntity ?
    if (moduleGroupEntity?.path != groupPathList) {
      when {
        moduleGroupEntity == null && groupPathList != null -> diff addEntity ModuleGroupPathEntity(path = groupPathList,
                                                                                                   entitySource = moduleEntity.entitySource
        ) {
          this.module = moduleEntity
        }

        moduleGroupEntity == null && groupPathList == null -> Unit

        moduleGroupEntity != null && groupPathList == null -> diff.removeEntity(moduleGroupEntity)

        moduleGroupEntity != null && groupPathList != null -> diff.modifyEntity(moduleGroupEntity) {
          path = groupPathList
        }

        else -> error("Should not be reached")
      }
      moduleGroupsAreModified = true
    }
  }

  companion object {
    private val moduleRenamingTimeMs: AtomicLong = AtomicLong()
    private val disposingTimeMs: AtomicLong = AtomicLong()
    private val loadModuleTimeMs: AtomicLong = AtomicLong()
    private val newModuleTimeMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val moduleRenamingTimeGauge = meter.gaugeBuilder("jps.modifiable.module.model.bridge.renaming.ms")
        .ofLongs().buildObserver()

      val disposingTimeGauge = meter.gaugeBuilder("jps.modifiable.module.model.bridge.disposing.ms")
        .ofLongs().buildObserver()

      val loadModuleTimeGauge = meter.gaugeBuilder("jps.modifiable.module.model.bridge.load.module.ms")
        .ofLongs().buildObserver()

      val newModuleTimeGauge = meter.gaugeBuilder("jps.modifiable.module.model.bridge.new.module.ms")
        .ofLongs().buildObserver()

      meter.batchCallback(
        {
          moduleRenamingTimeGauge.record(moduleRenamingTimeMs.get())
          disposingTimeGauge.record(disposingTimeMs.get())
          loadModuleTimeGauge.record(loadModuleTimeMs.get())
          newModuleTimeGauge.record(newModuleTimeMs.get())
        },
        moduleRenamingTimeGauge, disposingTimeGauge, loadModuleTimeGauge, newModuleTimeGauge
      )
    }

    init {
      setupOpenTelemetryReporting(JpsMetrics.getInstance().meter)
    }
  }
}
