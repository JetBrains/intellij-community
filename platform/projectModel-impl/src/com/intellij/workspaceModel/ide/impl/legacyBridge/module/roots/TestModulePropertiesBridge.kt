// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.TestModuleProperties
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.bridgeEntities.TestModulePropertiesEntity
import com.intellij.workspaceModel.storage.bridgeEntities.modifyEntity

class TestModulePropertiesBridge(private val currentModule: Module): TestModuleProperties() {
  private val workspaceModel = WorkspaceModel.getInstance(currentModule.project)

  override fun getProductionModuleName(): String? {
    return getModuleEntity()?.testProperties?.productionModuleId?.name
  }

  override fun getProductionModule(): Module? {
    val moduleId = getModuleEntity()?.testProperties?.productionModuleId ?: return null
    val moduleEntity = workspaceModel.entityStorage.current.resolve(moduleId) ?: return null
    return moduleEntity.findModule(workspaceModel.entityStorage.current)
  }

  override fun setProductionModuleName(moduleName: String?) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    val moduleEntity = getModuleEntity() ?: error("Module entity with name: ${currentModule.name} should be available")
    workspaceModel.updateProjectModel("") { builder ->
      moduleEntity.testProperties?.let { builder.removeEntity(it) }
      if (moduleName == null) return@updateProjectModel
      val productionModuleId = ModuleId(moduleName)
      builder.resolve(productionModuleId) ?: error("Can't find module by name: $moduleName")
      builder.modifyEntity(moduleEntity) {
        this.testProperties = TestModulePropertiesEntity(productionModuleId, moduleEntity.entitySource)
      }
    }
  }

  fun setProductionModuleNameToBuilder(moduleName: String?, builder: MutableEntityStorage) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    val moduleEntity = builder.resolve(ModuleId(currentModule.name)) ?: error("Module entity with name: ${currentModule.name} should be available")
    moduleEntity.testProperties?.let { builder.removeEntity(it) }
    if (moduleName == null) return
    val productionModuleId = ModuleId(moduleName)
    if (builder.resolve(productionModuleId) == null) {
      thisLogger().warn("Can't find module by name: $moduleName, but it can be a valid case e.g at gradle import")
    }
    builder.modifyEntity(moduleEntity) {
      this.testProperties = TestModulePropertiesEntity(productionModuleId, moduleEntity.entitySource)
    }
  }

  private fun getModuleEntity(): ModuleEntity? {
    return workspaceModel.entityStorage.current.resolve(ModuleId(currentModule.name))
  }
}