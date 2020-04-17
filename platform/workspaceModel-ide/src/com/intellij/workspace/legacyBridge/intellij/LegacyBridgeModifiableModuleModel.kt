package com.intellij.workspace.legacyBridge.intellij

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
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.NonPersistentEntitySource
import com.intellij.workspace.ide.VirtualFileUrlManagerImpl
import com.intellij.workspace.ide.WorkspaceModel
import com.intellij.workspace.jps.JpsProjectEntitiesLoader
import com.intellij.workspace.legacyBridge.libraries.libraries.LegacyBridgeModifiableBase

internal class LegacyBridgeModifiableModuleModel(
  private val project: Project,
  private val moduleManager: LegacyBridgeModuleManagerComponent,
  diff: TypedEntityStorageBuilder
) : LegacyBridgeModifiableBase(diff), ModifiableModuleModel {

  override fun getProject(): Project = project

  private val myModulesToAdd = HashBiMap.create<String, LegacyBridgeModule>()
  private val myModulesToDispose = HashBiMap.create<String, LegacyBridgeModule>()
  private val myNewNameToModule = HashBiMap.create<String, LegacyBridgeModule>()
  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManagerImpl.getInstance(project)

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

    val module = LegacyBridgeModuleImpl(moduleEntity.persistentId(), moduleName, project, null, entityStoreOnDiff, diff)
    moduleManager.addUncommittedModule(module)
    myModulesToAdd[moduleName] = module

    module.init {}
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

    val entitySource = JpsProjectEntitiesLoader.createEntitySourceForModule(project, virtualFileManager.fromPath(PathUtil.getParentPath(canonicalPath)), null)

    val moduleEntity = diff.addModuleEntity(
      name = moduleName,
      dependencies = listOf(ModuleDependencyItem.ModuleSourceDependency),
      type = moduleTypeId,
      source = entitySource
    )

    val moduleInstance = moduleManager.createModuleInstance(moduleEntity, entityStoreOnDiff, diff = diff, isNew = true)
    moduleManager.addUncommittedModule(moduleInstance)
    myModulesToAdd[moduleName] = moduleInstance

    return moduleInstance
  }

  private fun getModuleByFilePath(filePath: String): LegacyBridgeModule? {
    for (module in modules) {
      val sameFilePath = when (SystemInfo.isFileSystemCaseSensitive) {
        true -> module.moduleFilePath == filePath
        false -> module.moduleFilePath.equals(filePath, ignoreCase = true)
      }

      if (sameFilePath) {
        return module as LegacyBridgeModule
      }
    }

    return null
  }

  // TODO Actually load module content
  override fun loadModule(filePath: String): Module =
    newModule(filePath, "")

  override fun disposeModule(module: Module) {
    module as LegacyBridgeModule

    if (findModuleByName(module.name) == null) {
      error("Module '${module.name}' is not found. Probably it's already disposed.")
    }

    if (myModulesToAdd.inverse().remove(module) != null) {
      (ModuleManager.getInstance(project) as LegacyBridgeModuleManagerComponent).removeUncommittedModule(module.name)
      Disposer.dispose(module)
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

    val moduleManager = ModuleManager.getInstance(project) as LegacyBridgeModuleManagerComponent
    for (moduleToAdd in myModulesToAdd.values) {
      moduleManager.removeUncommittedModule(moduleToAdd.name)
      Disposer.dispose(moduleToAdd)
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

  fun collectChanges(): TypedEntityStorageBuilder {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val storage = entityStoreOnDiff.current

    for (moduleToDispose in myModulesToDispose.values) {
      val moduleEntity = storage.resolve(moduleToDispose.moduleEntityId)
                         ?: error("Could not find module to remove by id: ${moduleToDispose.moduleEntityId}")
      diff.removeEntity(moduleEntity)
    }

    for (entry in myNewNameToModule.entries) {
      val entity = storage.resolve(entry.value.moduleEntityId) ?:
        error("Unable to resolve module by id: ${entry.value.moduleEntityId}")
      diff.modifyEntity(ModifiableModuleEntity::class.java, entity) {
        name = entry.key
      }
    }

    return diff
  }

  override fun renameModule(module: Module, newName: String) {
    module as LegacyBridgeModule

    val oldModule = findModuleByName(newName)

    myNewNameToModule.inverse().remove(module)
    myNewNameToModule.remove(newName)

    if (module.name != newName) { // if renaming to itself, forget it altogether
      myNewNameToModule[newName] = module
    }

    if (oldModule != null) {
      throw ModuleWithNameAlreadyExists(ProjectModelBundle.message("module.already.exists.error", newName), newName)
    }
  }

  override fun getModuleToBeRenamed(newName: String): Module? = myNewNameToModule[newName]
  override fun getNewName(module: Module): String? = myNewNameToModule.inverse()[module]
  override fun getActualName(module: Module): String = getNewName(module) ?: module.name

  override fun getModuleGroupPath(module: Module): Array<String>? =
    LegacyBridgeModuleManagerComponent.getModuleGroupPath(module, entityStoreOnDiff)

  override fun hasModuleGroups(): Boolean = LegacyBridgeModuleManagerComponent.hasModuleGroups(entityStoreOnDiff)

  override fun setModuleGroupPath(module: Module, groupPath: Array<out String>?) {
    val moduleId = (module as LegacyBridgeModule).moduleEntityId

    val storage = entityStoreOnDiff.current

    val moduleEntity = storage.resolve(moduleId) ?: error("Could not resolve module by moduleId: $moduleId")
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
