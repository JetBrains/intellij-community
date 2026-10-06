// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.impl

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.diagnostic.isControlFlowException
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.ide.navigation.RequestedEditor
import com.intellij.platform.projectView.pane.ProjectViewPaneNavigateOptions
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.annotations.ApiStatus

/** The request to open [navigatable] in a right split: the whole file for a file node, as the monolith action does. */
@ApiStatus.Internal
@RequiresReadLock
@RequiresBackgroundThread
fun rightSplitNavigationRequest(project: Project, navigatable: Navigatable): NavigationRequest? {
  val file = ((navigatable as? AbstractTreeNode<*>)?.value as? PsiFile)?.virtualFile
  return if (file != null) NavigationRequest.sourceNavigationRequest(project, file, -1) else navigatable.navigationRequest()
}

@ApiStatus.Internal
suspend fun navigateSafely(
  project: Project,
  navigationRequests: List<NavigationRequest>,
  options: ProjectViewPaneNavigateOptions,
): Boolean {
  val navigationOptions = NavigationOptions.defaultOptions().requestFocus(options.requestFocus).let {
    if (options.openInRightSplit) it.openInRightSplit(true).requestedEditor(RequestedEditor.None) else it
  }
  return try {
    NavigationService.getInstance(project).navigate(requests = navigationRequests, options = navigationOptions)
  }
  catch (e: Throwable) {
    if (e.isControlFlowException) {
      currentCoroutineContext().ensureActive()
      return false // the navigation itself was canceled, not our job
    }
    else {
      throw e // a genuine exception
    }
  }
}
