// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.workspace.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.workspace.ProjectWorkspace
import com.intellij.openapi.workspace.ProjectWorkspaceManager
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.serviceContainer.getComponentManagerImpl
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

private val WORKSPACE_KEY: Key<ProjectWorkspace> = Key.create("ProjectWorkspaceManagerImpl.WORKSPACE")

private val WORKSPACE_FACTORY_EP: ExtensionPointName<ProjectWorkspaceFactory> = ExtensionPointName("com.intellij.projectWorkspaceFactory")

private val LOG = logger<ProjectWorkspaceManagerImpl>()

/** Tracks live workspaces and the projects bound to each workspace. */
@ApiStatus.Internal
class ProjectWorkspaceManagerImpl : ProjectWorkspaceManager {
  companion object {
    fun getInstance(): ProjectWorkspaceManagerImpl {
      return ApplicationManager.getApplication().service<ProjectWorkspaceManager>() as ProjectWorkspaceManagerImpl
    }
  }

  private val lock = Any()

  /** Guarded by [lock]. */
  private val live = LinkedHashMap<ProjectWorkspace, MutableList<Project>>()

  /**
   * Creates a single-project workspace on first access if the project has no binding.
   * The default project facade holds the binding for its implementation.
   *
   * @throws AlreadyDisposedException if the project was disposed before it acquired a workspace.
   */
  override fun getWorkspace(project: Project): ProjectWorkspace {
    project.getUserData(WORKSPACE_KEY)?.let { return it }
    val holder = if (project.isDefault) ProjectManager.getInstance().defaultProject else project
    synchronized(lock) {
      holder.getUserData(WORKSPACE_KEY)?.let { return it }
      if (holder.isDisposed) {
        throw AlreadyDisposedException("The project is disposed and has no workspace: $holder")
      }
      val workspace = WorkspaceAsProject(holder)
      startWorkspace(workspace, holder)
      return workspace
    }
  }

  /**
   * Binds [project] before its services initialize. An existing binding takes precedence.
   * Otherwise the project joins the live workspace of [frameOwner] if it supports member projects.
   * If no such workspace exists, the manager tries the factories and falls back to [WorkspaceAsProject].
   */
  fun bindProject(project: Project, identityFile: Path, frameOwner: Project?): ProjectWorkspace {
    val ownerWorkspace = frameOwner?.let(::getWorkspace)
    synchronized(lock) {
      project.getUserData(WORKSPACE_KEY)?.let {
        LOG.error("The project has a workspace before its binding: $project, $it")
        return it
      }

      if (ownerWorkspace != null && ownerWorkspace !is WorkspaceAsProject) {
        val projects = live.get(ownerWorkspace)
        if (projects != null) {
          projects.add(project)
          project.putUserData(WORKSPACE_KEY, ownerWorkspace)
          return ownerWorkspace
        }
      }

      val workspace = createWorkspaceOfHost(project, identityFile) ?: WorkspaceAsProject(project)
      startWorkspace(workspace, project)
      return workspace
    }
  }

  private fun createWorkspaceOfHost(project: Project, identityFile: Path): ProjectWorkspace? {
    for (factory in WORKSPACE_FACTORY_EP.extensionList) {
      val workspace = factory.createWorkspace(project, identityFile) ?: continue
      val container = workspace.getComponentManagerImpl()
      if (container is ProjectWorkspaceComponentManager && container.hostProject === project) {
        return workspace
      }
      LOG.error("The workspace of $factory has no container of the project $project: $container")
    }
    return null
  }

  /** The caller must hold [lock]. */
  private fun startWorkspace(workspace: ProjectWorkspace, project: Project) {
    live[workspace] = mutableListOf(project)
    (workspace.getComponentManagerImpl() as ProjectWorkspaceComponentManager).registerWorkspaceComponents()
    project.putUserData(WORKSPACE_KEY, workspace)
  }

  /** Returns a snapshot that includes workspaces of projects still opening. */
  fun liveWorkspaces(): List<ProjectWorkspace> {
    synchronized(lock) {
      return live.keys.toList()
    }
  }

  /**
   * Releases [project] and disposes the workspace container when its last project leaves.
   * The project retains its binding. The caller must hold the write-intent lock.
   */
  fun releaseProject(project: Project) {
    val workspace = project.getUserData(WORKSPACE_KEY) ?: return
    val isLast = synchronized(lock) {
      val projects = live.get(workspace) ?: return
      if (!projects.remove(project)) {
        return
      }
      if (projects.isEmpty()) {
        live.remove(workspace)
        true
      }
      else {
        false
      }
    }
    if (isLast) {
      val container = workspace.getComponentManagerImpl()
      container.startDispose()
      Disposer.dispose(container)
    }
  }
}
