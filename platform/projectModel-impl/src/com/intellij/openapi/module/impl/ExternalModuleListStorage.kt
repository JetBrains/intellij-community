// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.ExternalProjectSystemRegistry
import com.intellij.openapi.roots.ProjectModelElement
import com.intellij.openapi.roots.ProjectModelExternalSource
import org.jdom.Element

interface ExternalModuleListStorage {
  fun getExternalModules(): Set<ModulePath>?
}

@State(name = "ExternalProjectModuleManager", storages = [(Storage("modules.xml"))], externalStorageOnly = true)
internal class ExternalModuleListStorageImpl(private val project: Project)
  : PersistentStateComponent<Element>, ProjectModelElement, ExternalModuleListStorage {

  var loadedState: Set<ModulePath>? = null
    private set

  override fun getExternalModules(): Set<ModulePath>? = loadedState

  override fun getState(): Element {
    val e = Element("state")
    if (!project.isExternalStorageEnabled) {
      return e
    }

    val moduleManager = ModuleManagerEx.getInstanceEx(project)
    val filteredModuleList = getFilteredModuleList(project, moduleManager.modules, true)
    ModuleManagerImpl.writeExternal(e, filteredModuleList, moduleManager)

    return e
  }

  override fun loadState(state: Element) {
    loadedState = ModuleManagerImpl.getPathsToModuleFiles(state)
  }

  override fun getExternalSource(): ProjectModelExternalSource? {
    val externalProjectSystemRegistry = ExternalProjectSystemRegistry.getInstance()
    for (module in ModuleManager.getInstance(project).modules) {
      externalProjectSystemRegistry.getExternalSource(module)?.let {
        return it
      }
    }
    return null
  }
}

fun getFilteredModuleList(project: Project, modules: Array<Module>, isExternal: Boolean): List<Module> {
  if (!project.isExternalStorageEnabled) {
    return modules.asList()
  }

  val externalProjectSystemRegistry = ExternalProjectSystemRegistry.getInstance()
  return modules.filter { (externalProjectSystemRegistry.getExternalSource(it) != null) == isExternal }
}