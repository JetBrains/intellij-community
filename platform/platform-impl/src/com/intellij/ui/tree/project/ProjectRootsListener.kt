// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tree.project

import com.intellij.ProjectTopics
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.util.Function
import com.intellij.util.messages.MessageBusConnection

internal class ProjectRootsListener(val action: Runnable) : ModuleListener, ModuleRootListener {
  override fun rootsChanged(event: ModuleRootEvent) = action.run()
  override fun moduleAdded(project: Project, module: Module) = action.run()
  override fun moduleRemoved(project: Project, module: Module) = action.run()
  override fun modulesRenamed(project: Project, modules: List<Module?>, oldNameProvider: Function<in Module?, String>) = action.run()

  fun addTo(connection: MessageBusConnection) {
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, this)
    connection.subscribe(ProjectTopics.MODULES, this)
  }
}
