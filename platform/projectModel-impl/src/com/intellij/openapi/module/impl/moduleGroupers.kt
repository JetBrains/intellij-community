// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl

import com.intellij.openapi.module.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import java.util.*

/**
 * @author nik
 */
internal fun createGrouper(project: Project, moduleModel: ModifiableModuleModel? = null): ModuleGrouper {
  val hasGroups = moduleModel?.hasModuleGroups() ?: ModuleManager.getInstance(project).hasModuleGroups()
  if (!isQualifiedModuleNamesEnabled(project) || hasGroups) {
    return ExplicitModuleGrouper(project, moduleModel)
  }
  return QualifiedNameGrouper(project, moduleModel)
}

private abstract class ModuleGrouperBase(protected val project: Project, protected val model: ModifiableModuleModel?) : ModuleGrouper() {
  override fun getAllModules(): Array<Module> = model?.modules ?: ModuleManager.getInstance(project).modules

  protected fun getModuleName(module: Module) = model?.getNewName(module) ?: module.name

  override fun getShortenedName(module: Module) = getShortenedNameByFullModuleName(getModuleName(module))
}

private class QualifiedNameGrouper(project: Project, model: ModifiableModuleModel?) : ModuleGrouperBase(project, model) {
  override fun getGroupPath(module: Module): List<String> {
    return getGroupPathByModuleName(getModuleName(module))
  }

  override fun getGroupPath(description: ModuleDescription) = getGroupPathByModuleName(description.name)

  override fun getShortenedNameByFullModuleName(name: String) = StringUtil.getShortName(name)

  override fun getGroupPathByModuleName(name: String) = name.split('.').dropLast(1)

  override fun getModuleAsGroupPath(module: Module) = getModuleName(module).split('.')

  override fun getModuleAsGroupPath(description: ModuleDescription) = description.name.split('.')
}

private class ExplicitModuleGrouper(project: Project, model: ModifiableModuleModel?): ModuleGrouperBase(project, model) {
  override fun getGroupPath(module: Module): List<String> {
    val path = if (model != null) model.getModuleGroupPath(module) else ModuleManager.getInstance(project).getModuleGroupPath(module)
    return if (path != null) Arrays.asList(*path) else emptyList()
  }

  override fun getGroupPath(description: ModuleDescription) = when (description) {
    is LoadedModuleDescription -> getGroupPath(description.module)
    is UnloadedModuleDescription -> description.groupPath
    else -> throw IllegalArgumentException(description.javaClass.name)
  }

  override fun getShortenedNameByFullModuleName(name: String) = name

  override fun getGroupPathByModuleName(name: String): List<String> = emptyList()

  override fun getModuleAsGroupPath(module: Module) = null

  override fun getModuleAsGroupPath(description: ModuleDescription) = null
}
