// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.storage.EntityStorage

interface AutomaticModuleUnloader {
  fun processNewModules(currentModules: Set<String>, storage: EntityStorage)
  fun setLoadedModules(modules: List<String>)

  companion object {
    fun getInstance(project: Project) = project.service<AutomaticModuleUnloader>()
  }
}

internal class DummyAutomaticModuleUnloader : AutomaticModuleUnloader {
  override fun processNewModules(currentModules: Set<String>, storage: EntityStorage) {}

  override fun setLoadedModules(modules: List<String>) {}
}