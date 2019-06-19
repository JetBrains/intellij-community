// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import org.jetbrains.plugins.groovy.util.hasAcceptableModuleType
import org.jetbrains.plugins.groovy.util.hasJavaSdk

private fun Project.modules(): Array<out Module> = ModuleManager.getInstance(this).modules

internal fun isApplicableModule(module: Module): Boolean = hasJavaSdk(module) && hasAcceptableModuleType(module)

internal fun hasAnyApplicableModule(project: Project): Boolean = project.modules().any(::isApplicableModule)

internal fun getApplicableModules(project: Project): List<Module> = project.modules().filter(::isApplicableModule)

fun getWorkingDirectory(module: Module): String? {
  return ModuleRootManager.getInstance(module).contentRoots.firstOrNull()?.path ?: module.project.basePath
}
