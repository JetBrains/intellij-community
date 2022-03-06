// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.macro

import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project

class GroovyMacroModuleListener : ModuleListener {

  override fun moduleAdded(project: Project, module: Module) = project.service<GroovyMacroRegistryService>().refreshModule(module)

  override fun moduleRemoved(project: Project, module: Module) = project.service<GroovyMacroRegistryService>().refreshModule(module)
}