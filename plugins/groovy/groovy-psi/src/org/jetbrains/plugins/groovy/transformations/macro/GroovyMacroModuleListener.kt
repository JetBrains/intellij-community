// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.macro

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener

class GroovyMacroModuleListener : ModuleListener, ModuleRootListener {

  override fun moduleAdded(project: Project, module: Module) = project.service<GroovyMacroRegistryService>().refreshModule(module)

  override fun moduleRemoved(project: Project, module: Module) = project.service<GroovyMacroRegistryService>().refreshModule(module)

  override fun rootsChanged(event: ModuleRootEvent) {
    val service = event.project.service<GroovyMacroRegistryService>()
    for (module in ModuleManager.getInstance(event.project).modules) {
      service.refreshModule(module)
    }
  }

}