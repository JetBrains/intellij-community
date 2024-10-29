// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.platform.workspace.storage.MutableEntityStorage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class ModuleManagerEx : ModuleManager() {
  abstract fun areModulesLoaded(): Boolean

  open fun calculateUnloadModules(
    builder: MutableEntityStorage,
    unloadedEntityBuilder: MutableEntityStorage,
  ): Pair<List<String>, List<String>> {
    return Pair(emptyList<String>(), emptyList<String>())
  }

  open fun updateUnloadedStorage(modulesToLoad: List<String>, modulesToUnload: List<String>) {}

  abstract val modulesByNameMap: Map<String, Module>

  companion object {
    const val IML_EXTENSION: String = ".iml"
    const val MODULE_GROUP_SEPARATOR: String = "/"

    @JvmStatic
    fun getInstanceEx(project: Project): ModuleManagerEx {
      return getInstance(project) as ModuleManagerEx
    }
  }
}
