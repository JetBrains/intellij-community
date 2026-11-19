// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.workspace.impl

import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.DelegatingComponentManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.workspace.ProjectWorkspace
import org.jetbrains.annotations.ApiStatus

/** A workspace for a single project. */
@ApiStatus.Internal
class WorkspaceAsProject(project: Project) : ProjectWorkspace, DelegatingComponentManagerEx {
  private val container = ProjectWorkspaceComponentManager(hostProject = project)

  override val delegateComponentManager: ComponentManagerEx
    get() = container

  override val projects: List<Project> = listOf(project)

  init {
    container.initForWorkspace(this)
  }

  override fun <T : Any?> getUserData(key: Key<T?>): T? = container.getUserData(key)

  override fun <T : Any?> putUserData(key: Key<T?>, value: T?) {
    container.putUserData(key, value)
  }

  override fun toString(): String = "WorkspaceAsProject(project=${projects.single()})"
}
