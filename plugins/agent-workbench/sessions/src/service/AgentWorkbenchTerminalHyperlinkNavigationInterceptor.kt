// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.chat.AgentChatTabSelectionService
import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPath
import com.intellij.agent.workbench.common.parseAgentWorkbenchPathOrNull
import com.intellij.agent.workbench.sessions.frame.AgentWorkbenchDedicatedFrameProjectManager
import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtilService
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.project.ProjectStoreOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.hyperlinks.TerminalHyperlinkNavigationInterceptor
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.invariantSeparatorsPathString

internal class AgentWorkbenchTerminalHyperlinkNavigationInterceptor internal constructor(
  private val selectedSourcePath: (Project) -> String?,
  private val isDedicatedProject: (Project) -> Boolean,
  private val isDedicatedPath: (String) -> Boolean,
  private val findOpenProject: (String) -> Project?,
  private val openProject: suspend (String) -> Project?,
  private val focusProjectWindow: suspend (Project) -> Unit,
  private val navigate: suspend (Project, OpenFileDescriptor) -> Boolean,
) : TerminalHyperlinkNavigationInterceptor {
  @Suppress("unused")
  constructor() : this(
    selectedSourcePath = { project: Project -> project.service<AgentChatTabSelectionService>().selectedChatTab.value?.projectPath },
    isDedicatedProject = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProject,
    isDedicatedPath = AgentWorkbenchDedicatedFrameProjectManager::isDedicatedProjectPath,
    findOpenProject = ::findOpenProjectByPath,
    openProject = ::openProjectByPath,
    focusProjectWindow = ::focusProjectWindowForNavigation,
    navigate = ::navigateInProject,
  )

  override suspend fun intercept(project: Project, hyperlinkInfo: HyperlinkInfo, mouseEvent: EditorMouseEvent?): Boolean {
    if (!isDedicatedProject(project)) {
      return false
    }

    val sourceProjectPath = selectedSourcePath(project)
      ?.let(::normalizeAgentWorkbenchPath)
      ?.takeUnless { it.isBlank() || isDedicatedPath(it) }
      ?: return false

    val sourceDescriptor = (hyperlinkInfo as? FileHyperlinkInfo)?.descriptor ?: return false
    val file = sourceDescriptor.file
    if (!file.isValid) {
      return false
    }

    val sourceProject = findOpenProject(sourceProjectPath) ?: openProject(sourceProjectPath) ?: return false
    focusProjectWindow(sourceProject)

    return navigate(sourceProject, buildTargetDescriptor(sourceProject, sourceDescriptor))
  }
}

private fun buildTargetDescriptor(targetProject: Project, sourceDescriptor: OpenFileDescriptor): OpenFileDescriptor {
  val file = sourceDescriptor.file
  return when {
    sourceDescriptor.line >= 0 -> OpenFileDescriptor(targetProject, file, sourceDescriptor.line, sourceDescriptor.column)
    sourceDescriptor.offset >= 0 -> OpenFileDescriptor(targetProject, file, sourceDescriptor.offset)
    else -> OpenFileDescriptor(targetProject, file)
  }
}

private suspend fun navigateInProject(targetProject: Project, descriptor: OpenFileDescriptor): Boolean {
  if (!descriptor.canNavigate()) {
    return false
  }
  return withContext(Dispatchers.EDT) {
    OpenFileHyperlinkInfo(descriptor).navigate(targetProject)
    true
  }
}

private suspend fun focusProjectWindowForNavigation(project: Project) {
  withContext(Dispatchers.UI) {
    project.serviceAsync<ProjectUtilService>().focusProjectWindow()
  }
}

private suspend fun openProjectByPath(normalizedPath: String): Project? {
  val projectPath = parseAgentWorkbenchPathOrNull(normalizedPath) ?: return null
  return try {
    (serviceAsync<ProjectManager>() as ProjectManagerEx).openProjectAsync(projectPath, OpenProjectTask())
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (t: Throwable) {
    LOG.warn("Failed to open source project for terminal hyperlink navigation at $normalizedPath", t)
    null
  }
}

private fun findOpenProjectByPath(normalizedPath: String): Project? {
  return ProjectManager.getInstance().openProjects.firstOrNull { project ->
    val projectPath = (project as? ProjectStoreOwner)
      ?.componentStore
      ?.storeDescriptor
      ?.presentableUrl
      ?.invariantSeparatorsPathString
      ?: return@firstOrNull false
    projectPath == normalizedPath
  }
}

private val LOG = logger<AgentWorkbenchTerminalHyperlinkNavigationInterceptor>()
