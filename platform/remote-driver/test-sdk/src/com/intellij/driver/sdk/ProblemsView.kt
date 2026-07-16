// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.client.utility
import com.intellij.driver.model.OnDispatcher
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val PROBLEMS_VIEW_UI_MODULE: String = "intellij.problemView.plugin/intellij.platform.problemView.ui"
private const val PROBLEMS_VIEW_FRONTEND_MODULE: String = "intellij.problemView.plugin/intellij.platform.problemView.frontend"
private const val PROBLEMS_VIEW_BACKEND_MODULE: String = "intellij.problemView.plugin/intellij.platform.problemView.backend"

@Remote("com.intellij.analysis.problemsView.toolWindow.ProblemsViewToolWindowUtils", plugin = PROBLEMS_VIEW_UI_MODULE)
interface ProblemsViewToolWindowUtils {
  fun getToolWindow(project: Project): ToolWindowRef?
  fun getTabById(project: Project, id: String): ProblemsViewTab?
  fun selectTab(project: Project, id: String)
}

@Remote("com.intellij.openapi.wm.ToolWindow")
interface ToolWindowRef {
  fun isVisible(): Boolean
  fun isActive(): Boolean
}

@Remote("com.intellij.analysis.problemsView.toolWindow.ProblemsViewTab", plugin = PROBLEMS_VIEW_UI_MODULE)
interface ProblemsViewTab {
  fun getTabId(): String
}

@Remote("com.intellij.analysis.problemsView.toolWindow.HighlightingPanel", plugin = PROBLEMS_VIEW_UI_MODULE)
interface HighlightingPanel : ProblemsViewTab {
  fun getCurrentRoot(): HighlightingFileRoot?
  fun getCurrentFile(): VirtualFile?
}

@Remote("com.intellij.analysis.problemsView.toolWindow.splitApi.HighlightingFileRoot", plugin = PROBLEMS_VIEW_UI_MODULE)
interface HighlightingFileRoot {
  fun getFile(): VirtualFile
}

@Remote("com.intellij.platform.problemsView.frontend.FrontendProblemsViewHighlightingFileRoot", plugin = PROBLEMS_VIEW_FRONTEND_MODULE)
interface ProblemsViewHighlightingFileRoot {
  fun getFile(): VirtualFile
  fun getProblemCount(): Int
  fun getFileProblems(file: VirtualFile): List<HighlightingProblem>
}

@Remote("com.intellij.platform.problemsView.frontend.FrontendHighlightingProblem", plugin = PROBLEMS_VIEW_FRONTEND_MODULE)
interface HighlightingProblem {
  fun getId(): String
  fun getText(): String
  fun getLine(): Int
  fun getColumn(): Int
}


@Remote("com.intellij.platform.problemsView.backend.ProblemLifetimeManager", plugin = PROBLEMS_VIEW_BACKEND_MODULE)
interface ProblemLifetimeManager {
  fun hasProblemId(id: String): Boolean
  fun getProblemIdsSize(): Int
}

fun Driver.selectProblemsViewTab(id: String, project: Project? = null) {
  val forProject = project ?: singleProject()
  utility<ProblemsViewToolWindowUtils>().selectTab(forProject, id)
}

fun Driver.getHighlightingPanel(project: Project? = null): HighlightingPanel? {
  val forProject = project ?: singleProject()
  return withContext(OnDispatcher.EDT) {
    val tab = utility<ProblemsViewToolWindowUtils>().getTabById(forProject, HIGHLIGHTING_PANEL_ID) ?: return@withContext null
    cast(tab, HighlightingPanel::class)
  }
}

fun Driver.waitForProblemsViewFile(expectedFile: VirtualFile, project: Project? = null, timeout: Duration = 30.seconds) {
  val forProject = project ?: singleProject()
  val expectedPath = expectedFile.getPath()
  waitFor("Problems View 'Current File' panel shows ${expectedFile.getName()}", timeout) {
    withContext(OnDispatcher.EDT) {
      val panel = getHighlightingPanel(forProject) ?: return@withContext false
      panel.getCurrentRoot()?.getFile()?.getPath() == expectedPath
    }
  }
}

fun Driver.getProblemsViewProblems(file: VirtualFile, project: Project? = null): List<HighlightingProblem> {
  val forProject = project ?: singleProject()
  return withContext(OnDispatcher.EDT) {
    val panel = getHighlightingPanel(forProject) ?: return@withContext emptyList()
    val root = panel.getCurrentRoot() ?: return@withContext emptyList()
    cast(root, ProblemsViewHighlightingFileRoot::class).getFileProblems(file)
  }
}

const val HIGHLIGHTING_PANEL_ID: String = "CurrentFile"

/** Shown-problem ids that do NOT resolve in the backend id store (empty == every shown problem has an id). */
fun Driver.unresolvedProblemIds(file: VirtualFile, project: Project? = null): List<String> {
  val forProject = project ?: singleProject()
  val manager = service<ProblemLifetimeManager>(forProject)
  return getProblemsViewProblems(file, forProject)
    .map { it.getId() }
    .filter { !manager.hasProblemId(it) }
}

/** Total number of problem ids in the backend id store. */
fun Driver.backendProblemIdsSize(project: Project? = null): Int {
  val forProject = project ?: singleProject()
  return service<ProblemLifetimeManager>(forProject).getProblemIdsSize()
}

/** Closes the editor for [file] (triggers backend root disposal, which reaps the file's problem ids). */
fun Driver.closeProblemsViewFile(file: VirtualFile, project: Project? = null) {
  val forProject = project ?: singleProject()
  withContext(OnDispatcher.EDT) {
    service<FileEditorManager>(forProject).closeFile(file)
  }
}
