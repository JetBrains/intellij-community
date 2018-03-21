// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl

import com.intellij.openapi.module.*
import com.intellij.openapi.project.Project
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.TestOnly
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

  protected fun getModuleName(module: Module) = model?.getActualName(module) ?: module.name

  override fun getShortenedName(module: Module) = getShortenedNameByFullModuleName(getModuleName(module))
  override fun getShortenedName(module: Module, parentGroupName: String?) =
    getShortenedNameByFullModuleName(getModuleName(module), parentGroupName)
}


private class QualifiedNameGrouper(project: Project, model: ModifiableModuleModel?) : ModuleGrouperBase(project, model) {
  override fun getGroupPath(module: Module) = getGroupPathByModuleName(getModuleName(module))

  override fun getGroupPath(description: ModuleDescription) = getGroupPathByModuleName(description.name)

  override fun getShortenedNameByFullModuleName(name: String) = name.substringAfterLastDotNotFollowedByIncorrectChar()

  override fun getShortenedNameByFullModuleName(name: String, parentGroupName: String?): String {
    return if (parentGroupName != null) name.removePrefix("$parentGroupName.") else name
  }

  override fun getGroupPathByModuleName(name: String) = name.splitByDotsJoiningIncorrectIdentifiers().dropLast(1)

  override fun getModuleAsGroupPath(module: Module) = getModuleName(module).splitByDotsJoiningIncorrectIdentifiers()

  override fun getModuleAsGroupPath(description: ModuleDescription) = description.name.splitByDotsJoiningIncorrectIdentifiers()

  override val compactGroupNodes: Boolean
    get() = compactImplicitGroupNodes

  companion object {
    private val compactImplicitGroupNodes = SystemProperties.getBooleanProperty("project.compact.module.group.nodes", true)
  }
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

  override fun getShortenedNameByFullModuleName(name: String, parentGroupName: String?) = name

  override fun getGroupPathByModuleName(name: String): List<String> = emptyList()

  override fun getModuleAsGroupPath(module: Module) = null

  override fun getModuleAsGroupPath(description: ModuleDescription) = null

  override val compactGroupNodes: Boolean
    get() = false
}

/**
 * Split by dots where it doesn't lead to incorrect identifiers (i.e. "a.b-1.1" will be split to "a" and "b-1.1", not to "a", "b-1" and "1")
 */
private fun String.splitByDotsJoiningIncorrectIdentifiers(): List<String> {
  var start = 0
  var next = 1
  val names = ArrayList<String>()
  while (next < length) {
    val end = indexOf('.', next)
    if (end == -1 || end == length - 1) {
      break
    }
    next = end + 1
    if (this[end + 1].isJavaIdentifierStart()) {
      names.add(substring(start, end))
      start = end + 1
    }
  }
  names.add(substring(start))
  return names
}

/**
 * Returns the same value as `splitByDotsNotFollowedByIncorrectChars().last()` but works more efficiently
 */
private fun String.substringAfterLastDotNotFollowedByIncorrectChar(): String {
  if (length <= 1) return this
  var i = lastIndexOf('.', length - 2)
  while (i != -1 && !this[i + 1].isJavaIdentifierStart()) {
    i = lastIndexOf('.', i-1)
  }
  if (i <= 0) return this
  return substring(i+1)
}

@TestOnly
public fun splitStringByDotsJoiningIncorrectIdentifiers(string: String): Pair<List<String>, String> {
  return Pair(string.splitByDotsJoiningIncorrectIdentifiers(), string.substringAfterLastDotNotFollowedByIncorrectChar())
}