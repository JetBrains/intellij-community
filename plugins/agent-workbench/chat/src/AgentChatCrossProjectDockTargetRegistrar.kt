// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.parseAgentWorkbenchPathOrNull
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.ProjectUtil.isSameProject
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.DockableEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.docking.DockContainer
import com.intellij.ui.docking.DockContainer.ContentResponse
import com.intellij.ui.docking.DockManager
import com.intellij.ui.docking.DockableContent
import java.awt.Image
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.io.path.invariantSeparatorsPathString

internal class AgentChatCrossProjectDockTargetRegistrar @JvmOverloads constructor(
  private val openProjectsProvider: () -> Array<Project> = {
    runCatching { ProjectManager.getInstance().openProjects }.getOrDefault(emptyArray())
  },
  private val projectIdentityPath: (Project) -> String? = { project ->
    runCatching { RecentProjectsManagerBase.getInstanceEx().getProjectPath(project)?.invariantSeparatorsPathString }.getOrNull()
  },
  private val isPathEquivalentToProject: (Project, Path) -> Boolean = { project, path ->
    runCatching { isSameProject(projectFile = path, project = project) }.getOrDefault(false)
  },
  private val dockContainerProvider: (Project) -> DockContainer? = { project ->
    (project.serviceIfCreated<FileEditorManager>() as? FileEditorManagerEx)?.dockContainer
  },
  private val registerContainer: (Project, DockContainer, Disposable) -> Boolean = { project, container, parentDisposable ->
    val dockManager = runCatching { DockManager.getInstance(project) }.getOrNull()
    if (dockManager == null) {
      false
    }
    else {
      dockManager.register(container, parentDisposable)
      true
    }
  },
) {
  fun register(ownerProject: Project, file: AgentChatVirtualFile): Disposable? {
    if (ownerProject.isDisposed) {
      return null
    }
    val sourceProjectPath = normalizeAgentWorkbenchPath(file.projectPath)
    if (sourceProjectPath.isBlank()) {
      return null
    }
    val sourcePath = parseAgentWorkbenchPathOrNull(sourceProjectPath) ?: return null
    if (projectMatchesSource(ownerProject, sourceProjectPath, sourcePath)) {
      return null
    }

    val parentDisposable = Disposer.newDisposable("Agent chat cross-project dock target")
    val registered = registerContainer(
      ownerProject,
      AgentChatCrossProjectDockTargetContainer(
        ownerProject = ownerProject,
        sourceProjectPath = sourceProjectPath,
        sourcePath = sourcePath,
        openProjectsProvider = openProjectsProvider,
        projectMatchesSource = ::projectMatchesSource,
        dockContainerProvider = dockContainerProvider,
      ),
      parentDisposable,
    )
    if (!registered) {
      Disposer.dispose(parentDisposable)
      return null
    }
    return parentDisposable
  }

  private fun projectMatchesSource(project: Project, sourceProjectPath: String, sourcePath: Path): Boolean {
    val projectPath = projectIdentityPath(project)?.let(::normalizeAgentWorkbenchPath)
    return projectPath == sourceProjectPath || isPathEquivalentToProject(project, sourcePath)
  }
}

private class AgentChatCrossProjectDockTargetContainer(
  private val ownerProject: Project,
  private val sourceProjectPath: String,
  private val sourcePath: Path,
  private val openProjectsProvider: () -> Array<Project>,
  private val projectMatchesSource: (Project, String, Path) -> Boolean,
  private val dockContainerProvider: (Project) -> DockContainer?,
) : DockContainer {
  private val fallbackComponent = JPanel()
  private val emptyAcceptArea = RelativeRectangle()

  override fun getAcceptArea(): RelativeRectangle = targetContainerForArea()?.acceptArea ?: emptyAcceptArea

  override fun getAcceptAreaFallback(): RelativeRectangle = targetContainerForArea()?.acceptAreaFallback ?: emptyAcceptArea

  override fun getContentResponse(content: DockableContent<*>, point: RelativePoint): ContentResponse {
    return targetContainerForContent(content)?.getContentResponse(content, point) ?: ContentResponse.DENY
  }

  override fun getContainerComponent(): JComponent = targetContainerForArea()?.containerComponent ?: fallbackComponent

  override fun add(content: DockableContent<*>, dropTarget: RelativePoint?) {
    targetContainerForContent(content)?.add(content, dropTarget)
  }

  override fun isEmpty(): Boolean = false

  override fun startDropOver(content: DockableContent<*>, point: RelativePoint): Image? {
    return targetContainerForContent(content)?.startDropOver(content, point)
  }

  override fun processDropOver(content: DockableContent<*>, point: RelativePoint): Image? {
    return targetContainerForContent(content)?.processDropOver(content, point)
  }

  override fun resetDropOver(content: DockableContent<*>) {
    targetContainerForContent(content)?.resetDropOver(content)
  }

  override fun isDisposeWhenEmpty(): Boolean = false

  private fun targetContainerForArea(): DockContainer? {
    val targetProject = findOpenSourceProject() ?: return null
    return dockContainerProvider(targetProject)
  }

  private fun targetContainerForContent(content: DockableContent<*>): DockContainer? {
    if (!acceptsContent(content)) {
      return null
    }
    return targetContainerForArea()
  }

  private fun acceptsContent(content: DockableContent<*>): Boolean {
    if (content !is DockableEditor) {
      return false
    }
    val file = content.key as? AgentChatVirtualFile ?: return false
    return normalizeAgentWorkbenchPath(file.projectPath) == sourceProjectPath
  }

  private fun findOpenSourceProject(): Project? {
    if (ownerProject.isDisposed) {
      return null
    }
    for (project in openProjectsProvider()) {
      if (project.isDisposed || project == ownerProject) {
        continue
      }
      if (projectMatchesSource(project, sourceProjectPath, sourcePath)) {
        return project
      }
    }
    return null
  }
}
