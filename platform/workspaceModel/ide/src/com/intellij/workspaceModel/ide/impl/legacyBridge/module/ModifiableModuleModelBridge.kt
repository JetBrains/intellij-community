// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.google.common.collect.HashBiMap
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModifiableModuleModel
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleWithNameAlreadyExists
import com.intellij.openapi.module.impl.getModuleNameByFilePath
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.projectModel.ProjectModelBundle
import com.intellij.util.PathUtil
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.configLocation
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectEntitiesLoader
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeModifiableBase
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge.Companion.findModuleEntity
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleManagerComponentBridge.Companion.mutableModuleMap
import com.intellij.workspaceModel.ide.legacyBridge.ModuleBridge
import com.intellij.workspaceModel.storage.VirtualFileUrlManager
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.bridgeEntities.*
import java.io.File
import java.io.IOException

internal class ModifiableModuleModelBridge(
  private val project: Project,
  private val moduleManager: ModuleManagerComponentBridge,
  diff: WorkspaceEntityStorageBuilder
) : LegacyBridgeModifiableBase(diff), ModifiableModuleModel {
  override fun getProject(): Project = project

  private val myModulesToAdd = HashBiMap.create<String, ModuleBridge>()
  private val myModulesToDispose = HashBiMap.create<String, ModuleBridge>()
  private val myUncommittedModulesToDispose = ArrayList<ModuleBridge>()
  private val myNewNameToModule = HashBiMap.create<String, ModuleBridge>()
  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManager.getInstance(project)

  // TODO Add cache?
  override fun getModules(): Array<Module> {
    val modules = moduleManager.modules.toMutableList()
    modules.removeAll(myModulesToDispose.values)
    modules.addAll(myModulesToAdd.values)
    return modules.toTypedArray()
  }

  override fun newModule(filePath: String, moduleTypeId: String): Module =
    newModule(filePath, moduleTypeId, null)

  override fun newNonPersistentModule(moduleName: String, moduleTypeId: String): Module {
    val moduleEntity = diff.addModuleEntity(
      name = moduleName,
      dependencies = listOf(ModuleDependencyItem.ModuleSourceDependency),
      source = NonPersistentEntitySource
    )

    val module = ModuleBridgeImpl(moduleEntity.persistentId(), moduleName, project, null, entityStorageOnDiff, diff)
    diff.mutableModuleMap.addMapping(moduleEntity, module)
    myModulesToAdd[moduleName] = module

    module.init(null)
    module.setModuleType(moduleTypeId)
    return module
  }

  override fun newModule(filePath: String, moduleTypeId: String, options: MutableMap<String, String>?): Module {
    // TODO Handle filePath, add correct iml source with a path

    // TODO Must be in sync with module loading. It is not now
    val canonicalPath = FileUtil.toSystemIndependentName(FileUtil.resolveShortWindowsName(filePath))

    val existingModule = getModuleByFilePath(canonicalPath)
    if (existingModule != null) {
      return existingModule
    }

    val moduleName = getModuleNameByFilePath(canonicalPath)
    if (findModuleByName(moduleName) != null) {
      throw ModuleWithNameAlreadyExists("Module already exists: $moduleName", moduleName)
    }
    removeUnloadedModule(moduleName)

    val entitySource = JpsProjectEntitiesLoader.createEntitySourceForModule(project, virtualFileManager.fromPath(PathUtil.getParentPath(canonicalPath)), null)

    val moduleEntity = diff.addModuleEntity(
      name = moduleName,
      dependencies = listOf(ModuleDependencyItem.ModuleSourceDependency),
      type = moduleTypeId,
      source = entitySource
    )

    return createModuleInstance(moduleEntity, true)
  }

  private fun createModuleInstance(moduleEntity: ModuleEntity, isNew: Boolean): ModuleBridge {
    val moduleInstance = moduleManager.createModuleInstance(moduleEntity, entityStorageOnDiff, diff = diff, isNew = isNew)
    diff.mutableModuleMap.addMapping(moduleEntity, moduleInstance)
    myModulesToAdd[moduleEntity.name] = moduleInstance
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

  private fun removeUnloadedModule(moduleName: String) {
    // If module name equals to already unloaded module, the previous should be removed from store
    val unloadedModuleDescription = moduleManager.getUnloadedModuleDescription(moduleName)
    if (unloadedModuleDescription != null) {
      val moduleEntity = entityStorageOnDiff.current.resolve(
        ModuleId(unloadedModuleDescription.name))
                         ?: error("Could not find module to remove by id: ${unloadedModuleDescription.name}")
      diff.removeEntity(moduleEntity)
    }
  }

  override fun loadModule(filePath: String): Module {
    val moduleName = getModuleNameByFilePath(filePath)
    if (findModuleByName(moduleName) != null) {
      error("Module name '$moduleName' already exists. Trying to load module: $filePath")
    }

    removeUnloadedModule(moduleName)

    val builder = WorkspaceEntityStorageBuilder.create()
    JpsProjectEntitiesLoader.loadModule(File(filePath), project.configLocation!!, builder, virtualFileManager)
    diff.addDiff(builder)
    val moduleEntity = diff.entities(ModuleEntity::class.java).find { it.name == moduleName }
    if (moduleEntity == null) {
      throw IOException("Failed to load module from $filePath")
    }

    return createModuleInstance(moduleEntity, false)
  }

  override fun disposeModule(module: Module) {
    if (Disposer.isDisposing(module.project)) {
      //if the project is being disposed now, removing module won't work because WorkspaceModelImpl won't fire events and the module won't be disposed
      //it looks like this may happen in tests only so it's ok to skip removal of the module since the project will be disposed anyway
      return
    }

    module as ModuleBridge

    if (findModuleByName(module.name) == null) {
      error("Module '${module.name}' is not found. Probably it's already disposed.")
    }

    if (myModulesToAdd.inverse().remove(module) != null) {
      myUncommittedModulesToDispose.add(module)
    }

    myNewNameToModule.inverse().remove(module)

    myModulesToDispose[module.name] = module
  }

  override fun findModuleByName(name: String): Module? {
    val addedModule = myModulesToAdd[name]
    if (addedModule != null) return addedModule

    if (myModulesToDispose.containsKey(name)) return null

    val newNameModule = myNewNameToModule[name]
    if (newNameModule != null) return null

    return moduleManager.findModuleByName(name)
  }

  override fun dispose() {
    assertModelIsLive()

    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val moduleManager = ModuleManager.getInstance(project) as ModuleManagerComponentBridge
    for (moduleToAdd in myModulesToAdd.values) {
      Disposer.dispose(moduleToAdd)
    }
    for (module in myUncommittedModulesToDispose) {
      Disposer.dispose(module)
    }

    myModulesToAdd.clear()
    myModulesToDispose.clear()
    myNewNameToModule.clear()
  }

  override fun isChanged(): Boolean =
    myModulesToAdd.isNotEmpty() ||
    myModulesToDispose.isNotEmpty() ||
    myNewNameToModule.isNotEmpty()

  override fun commit() {
    val diff = collectChanges()

    WorkspaceModel.getInstance(project).updateProjectModel {
      it.addDiff(diff)
    }
  }

  fun collectChanges(): WorkspaceEntityStorageBuilder {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val storage = entityStorageOnDiff.current

    for (moduleToDispose in myModulesToDispose.values) {
      val moduleEntity = storage.findModuleEntity(moduleToDispose)
                         ?: error("Could not find module to remove by $moduleToDispose")
      diff.removeEntity(moduleEntity)
    }

    for (module in myUncommittedModulesToDispose) {
      Disposer.dispose(module)
    }

    return diff
  }

  override fun renameModule(module: Module, newName: String) {
    module as ModuleBridge

    val oldModule = findModuleByName(newName)

    val uncommittedOldName = myNewNameToModule.inverse().remove(module)
    myNewNameToModule.remove(newName)

    removeUnloadedModule(newName)
    val oldName = uncommittedOldName ?: module.name
    if (oldName != newName) { // if renaming to itself, forget it altogether
      val moduleToAdd = myModulesToAdd.remove(oldName)
      if (moduleToAdd != null) {
        moduleToAdd.rename(newName, true)
        myModulesToAdd[newName] = moduleToAdd
      }
      else {
        myNewNameToModule[newName] = module
      }
      val entity = entityStorageOnDiff.current.findModuleEntity(module) ?: error("Unable to find module entity for $module")
      diff.modifyEntity(ModifiableModuleEntity::class.java, entity) {
        name = newName
      }
    }

    if (oldModule != null) {
      throw ModuleWithNameAlreadyExists(ProjectModelBundle.message("module.already.exists.error", newName), newName)
    }
  }

  override fun getModuleToBeRenamed(newName: String): Module? = myNewNameToModule[newName]
  override fun getNewName(module: Module): String? = myNewNameToModule.inverse()[module]
  override fun getActualName(module: Module): String = getNewName(module) ?: module.name

  override fun getModuleGroupPath(module: Module): Array<String>? =
    ModuleManagerComponentBridge.getModuleGroupPath(module, entityStorageOnDiff)

  override fun hasModuleGroups(): Boolean = ModuleManagerComponentBridge.hasModuleGroups(entityStorageOnDiff)

  override fun setModuleGroupPath(module: Module, groupPath: Array<out String>?) {
    val storage = entityStorageOnDiff.current
    val moduleEntity = storage.findModuleEntity(module as ModuleBridge) ?: error("Could not resolve module entity for $module")
    val moduleGroupEntity = moduleEntity.groupPath
    val groupPathList = groupPath?.toList()

    // TODO How to deduplicate with ModuleCustomImlDataEntity ?
    if (moduleGroupEntity?.path != groupPathList) {
      when {
        moduleGroupEntity == null && groupPathList != null -> diff.addModuleGroupPathEntity(
          module = moduleEntity,
          path = groupPathList,
          source = moduleEntity.entitySource
        )

        moduleGroupEntity == null && groupPathList == null -> Unit

        moduleGroupEntity != null && groupPathList == null -> diff.removeEntity(moduleGroupEntity)

        moduleGroupEntity != null && groupPathList != null -> diff.modifyEntity(ModifiableModuleGroupPathEntity::class.java,
          moduleGroupEntity) {
          path = groupPathList
        }

        else -> error("Should not be reached")
      }
    }
  }
}
