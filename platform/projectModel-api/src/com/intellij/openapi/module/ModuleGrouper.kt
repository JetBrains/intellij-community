/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.module

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

/**
 * Use this class to determine how modules show by organized in a tree. It supports the both ways of module grouping: the old one where
 * groups are specified explicitly and the new one where modules are grouped accordingly to their qualified names.
 *
 * @author nik
 */
@ApiStatus.Experimental
abstract class ModuleGrouper {
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
   * If [module] itself can be considered as a group, returns its groups. Otherwise returns null.
   */
  abstract fun getModuleAsGroupPath(module: Module): List<String>?

  /**
   * If [description] itself can be considered as a group, returns its groups. Otherwise returns null.
   */
  abstract fun getModuleAsGroupPath(description: ModuleDescription): List<String>?

  abstract fun getAllModules(): Array<Module>

  /**
   * Determines whether module group nodes containing single child should be joined with the child nodes. E.g. the modules `foo.bar.baz`
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

fun isQualifiedModuleNamesEnabled(project: Project): Boolean = Registry.`is`("project.qualified.module.names") &&
                                                               !ModuleManager.getInstance(project).hasModuleGroups()
