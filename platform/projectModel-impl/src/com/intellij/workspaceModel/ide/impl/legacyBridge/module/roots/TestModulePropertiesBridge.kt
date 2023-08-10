// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.module.roots

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.TestModuleProperties
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.TestModulePropertiesEntity
import com.intellij.platform.workspace.jps.entities.modifyEntity
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.findModule

class TestModulePropertiesBridge(private val currentModule: Module): TestModuleProperties() {
  private val workspaceModel = WorkspaceModel.getInstance(currentModule.project)

  override fun getProductionModuleName(): String? {
    return getModuleEntity()?.testProperties?.productionModuleId?.name
  }

  override fun getProductionModule(): Module? {
    val moduleId = getModuleEntity()?.testProperties?.productionModuleId ?: return null
    val moduleEntity = workspaceModel.currentSnapshot.resolve(moduleId) ?: return null
    return moduleEntity.findModule(workspaceModel.currentSnapshot)
  }

  override fun setProductionModuleName(moduleName: String?) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    val moduleEntity = getModuleEntity() ?: error("Module entity with name: ${currentModule.name} should be available")
    if (moduleEntity.testProperties?.productionModuleId?.name == moduleName) return
    workspaceModel.updateProjectModel("Linking production module with the test") { builder ->
      moduleEntity.testProperties?.let { builder.removeEntity(it) }
      if (moduleName == null) return@updateProjectModel
      val productionModuleId = ModuleId(moduleName)
      builder.resolve(productionModuleId) ?: error("Can't find module by name: $moduleName")
      builder.modifyEntity(moduleEntity) {
        this.testProperties = TestModulePropertiesEntity(productionModuleId, moduleEntity.entitySource)
      }
    }
  }

  fun setProductionModuleNameToBuilder(moduleName: String?, currentModuleName: String, builder: MutableEntityStorage) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    val moduleEntity = builder.resolve(ModuleId(currentModuleName)) ?: error("Module entity with name: ${currentModuleName} should be available")
    if (moduleEntity.testProperties?.productionModuleId?.name == moduleName) return
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
    return workspaceModel.currentSnapshot.resolve(ModuleId(currentModule.name))
  }
}