// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

/**
 * Use this class to determine how modules show by organized in a tree. It supports both ways of module grouping: the old one where
 * groups are specified explicitly and the new one where modules are grouped accordingly to their qualified names.
 */
@ApiStatus.Experimental
abstract class ModuleGrouper @ApiStatus.Internal protected constructor() {
  /**
   * Returns names of parent groups for a module
   */
  abstract fun getGroupPath(module: Module): List<String>

  /**
   * Returns names of parent groups for a module
   */
  abstract fun getGroupPath(description: ModuleDescription): List<String>

  /**
   * Returns name which should be used for a module when it's shown under its group
   */
  @NlsSafe
  abstract fun getShortenedName(module: Module): String

  /**
   * Returns name which should be used for a module when it's shown under its ancestor group which qualified name is [parentGroupName].
   * If [parentGroupName] is `null` returns the full module name.
   */
  abstract fun getShortenedName(module: Module, parentGroupName: String?): String

  /**
   * Returns name which should be used for a module with name [name] when it's shown under its group
   */
  abstract fun getShortenedNameByFullModuleName(name: String): String

  /**
   * Returns name which should be used for a module with name [name] when it's shown under its ancestor group which qualified name is [parentGroupName].
   * If [parentGroupName] is `null` returns the full module name.
   */
  abstract fun getShortenedNameByFullModuleName(name: String, parentGroupName: String?): String

  abstract fun getGroupPathByModuleName(name: String): List<String>

  /**
   * If [module] itself can be considered as a group, returns its groups. Otherwise, returns null.
   */
  abstract fun getModuleAsGroupPath(module: Module): List<String>?

  /**
   * If [description] itself can be considered as a group, returns its groups. Otherwise, returns null.
   */
  @ApiStatus.Internal
  abstract fun getModuleAsGroupPath(description: ModuleDescription): List<String>?

  @ApiStatus.Internal
  abstract fun getAllModules(): Array<Module>

  /**
   * Determines whether module group nodes containing a single child should be joined with the child nodes. E.g., the modules `foo.bar.baz`
   * and `foo.bar.baz2` will form the following tree if [compactGroupNodes] is `false`
   * ```
   * foo
   *  bar
   *   baz
   *   baz2
   * ```
   * and the following tree if [compactGroupNodes] is `true`:
   * ```
   * foo.bar
   *  baz
   *  baz2
   * ```
   */
  abstract val compactGroupNodes: Boolean

  companion object {
    @JvmStatic
    @JvmOverloads
    fun instanceFor(project: Project, moduleModel: ModifiableModuleModel? = null): ModuleGrouper {
      return ModuleManager.getInstance(project).getModuleGrouper(moduleModel)
    }
  }
}

@ApiStatus.Internal
fun isQualifiedModuleNamesEnabled(project: Project): Boolean = Registry.`is`("project.qualified.module.names") &&
                                                               !ModuleManager.getInstance(project).hasModuleGroups()
